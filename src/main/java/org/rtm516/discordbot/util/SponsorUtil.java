/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/GeyserDiscordBot
 */

package org.rtm516.discordbot.util;

import com.apollographql.java.client.ApolloClient;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.rtm516.discordbot.graphql.SponsorsQuery;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.json.JSONObject;
import org.rtm516.discordbot.DiscordBot;
import pw.chew.chewbotcca.util.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SponsorUtil {
    private static final long DONATE_CHANNEL = 1139856745007681587L;
    public static final long DONATE_ROLE = 1144374185985065164L;
    public static final float DONATE_MIN = 5f;

    private static final ApolloClient apolloClient;
    private static final List<Sponsor> lastSponsors;

    private static Cache<UUID, Long> linkMap = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    static {
        apolloClient = new ApolloClient.Builder()
                .serverUrl("https://api.github.com/graphql")
                .addHttpHeader("Authorization", "Bearer " + PropertiesManager.getGithubToken())
                .build();
        lastSponsors = new ArrayList<>();
    }

    public static CompletableFuture<List<Sponsor>> getSponsors() {
        return getSponsorsInternal("");
    }

    private static CompletableFuture<List<Sponsor>> getSponsorsInternal(String cursor) {
        CompletableFuture<List<Sponsor>> future = new CompletableFuture<>();
        apolloClient.query(new SponsorsQuery("rtm516", 100, cursor)).enqueue(result -> {
            List<Sponsor> sponsors = new ArrayList<>();
            for (SponsorsQuery.Node node : result.data.user.sponsors.nodes) {
                sponsors.add(Sponsor.from(node));
            }

            // Recursively get the next page of sponsors if there is one
            if (result.data.user.sponsors.pageInfo.hasNextPage) {
                sponsors.addAll(getSponsorsInternal(result.data.user.sponsors.pageInfo.endCursor).join());
            }

            future.complete(sponsors);
        });

        return future;
    }

    public static void init() {
        // Get the initial sponsors
        getSponsors().thenAccept(lastSponsors::addAll);
        DiscordBot.getGeneralThreadPool().scheduleAtFixedRate(SponsorUtil::tick, 30, 30, TimeUnit.MINUTES);
    }

    private static void tick() {
        getSponsors().thenAccept(sponsors -> {
            // Find all new sponsors
            for (Sponsor sponsor : sponsors) {
                if (!lastSponsors.contains(sponsor)) {
                    TextChannel donateChannel = DiscordBot.getJDA().getTextChannelById(DONATE_CHANNEL);
                    donateChannel.sendMessageEmbeds(sponsor.toEmbed()).queue();

                    checkAdd(donateChannel.getGuild(), sponsor);
                }
            }

            // Update the last sponsors
            lastSponsors.clear();
            lastSponsors.addAll(sponsors);
        });
    }

    public static void checkAdd(Guild guild, Sponsor sponsor) {
        // Only give the role if the donation is over the minimum
        if (sponsor.amount() >= DONATE_MIN) {
            Member member = guild.getMemberById(getDiscord(sponsor.username()));
            Role role = guild.getRoleById(DONATE_ROLE);
            if (member != null) {
                guild.addRoleToMember(member, role).queue();
                member.getUser().openPrivateChannel().queue(channel -> channel.sendMessage("Thank you for sponsoring me. You have been given the " + role.getName() + " role in the " + guild.getName() + " Discord server!").queue());
            }
        }
    }

    public static String getGithub(User user) {
        return DiscordBot.storageManager.getGithubUsername(user.getIdLong());
    }

    public static long getDiscord(String username) {
        return DiscordBot.storageManager.getDiscordId(username);
    }

    public static String getGithubLink(User user) {
        UUID state = UUID.randomUUID();
        linkMap.put(state, user.getIdLong());
        return "https://github.com/login/oauth/authorize?client_id=Ov23limOJHnJZrpX0Ttt&state=" + state;
    }

    public static String linkGithub(UUID state, String code) {
        try {
            long userId = linkMap.getIfPresent(state);
            if (userId == 0) {
                return "An error occurred while linking your account";
            }

            linkMap.invalidate(state);

            JSONObject body = new JSONObject();
            body.put("client_id", PropertiesManager.getGithubClientId());
            body.put("client_secret", PropertiesManager.getGithubClientSecret());
            body.put("code", code);

            String accessToken = RestClient.post("https://github.com/login/oauth/access_token", body, "Accept: application/json").asJSONObject().getString("access_token");

            String ghUsername = RestClient.get("https://api.github.com/user", "Authorization: Bearer " + accessToken).asJSONObject().getString("login");

            DiscordBot.storageManager.setGithubUsername(userId, ghUsername);

            return "Successfully linked your github account " + ghUsername + " with your discord account @" + DiscordBot.getJDA().getUserById(userId).getName();
        } catch (Exception e) {
            return "An error occurred while linking your account";
        }
    }
}
