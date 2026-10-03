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
import com.rtm516.discordbot.graphql.SponsorshipsQuery;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.json.JSONObject;
import org.kohsuke.github.GHFileNotFoundException;
import org.rtm516.discordbot.DiscordBot;
import org.rtm516.discordbot.storage.GithubLink;
import org.rtm516.discordbot.storage.ServerSettings;
import pw.chew.chewbotcca.util.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SponsorUtil {
    public static final float DONATE_MIN = 5f;
    public static final Duration ONE_TIME_DURATION = Duration.ofDays(30);
    public static final String SPONSOR_LINK = "https://github.com/sponsors/rtm516";

    private static final ApolloClient apolloClient = new ApolloClient.Builder()
            .serverUrl("https://api.github.com/graphql")
            .addHttpHeader("Authorization", "Bearer " + PropertiesManager.getGithubToken())
            .build();

    private static final Cache<UUID, Long> linkMap = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    // Null until the first successful sync
    private static Map<Long, Sponsor> lastSponsors;

    public static void init() {
        DiscordBot.getGeneralThreadPool().execute(() -> {
            try {
                DiscordBot.getJDA().awaitReady();
            } catch (InterruptedException e) {
                return;
            }

            migrateLegacyLinks();
            sync();
        });

        DiscordBot.getGeneralThreadPool().scheduleAtFixedRate(SponsorUtil::sync, 30, 30, TimeUnit.MINUTES);
    }

    /**
     * Get the active sponsors keyed by GitHub account ID.
     * Fails if GitHub can't be queried so that is never mistaken for having no sponsors.
     *
     * @return The active sponsors
     */
    public static CompletableFuture<Map<Long, Sponsor>> getActiveSponsors() {
        return getSponsors(null, new ArrayList<>()).thenApply(sponsors -> {
            Map<Long, Sponsor> active = new HashMap<>();
            for (Sponsor sponsor : sponsors) {
                if (sponsor.active()) {
                    active.merge(sponsor.githubId(), sponsor, (a, b) -> a.amount() >= b.amount() ? a : b);
                }
            }
            return active;
        });
    }

    private static CompletableFuture<List<Sponsor>> getSponsors(String cursor, List<Sponsor> sponsors) {
        SponsorshipsQuery.Builder query = SponsorshipsQuery.builder();
        if (cursor != null) {
            query.cursor(cursor);
        }

        CompletableFuture<SponsorshipsQuery.SponsorshipsAsMaintainer> future = new CompletableFuture<>();
        apolloClient.query(query.build()).enqueue(result -> {
            if (result.exception != null) {
                future.completeExceptionally(result.exception);
            } else if (result.errors != null && !result.errors.isEmpty()) {
                future.completeExceptionally(new IllegalStateException("GitHub returned errors: " + result.errors));
            } else if (result.data == null || result.data.viewer == null || result.data.viewer.sponsorshipsAsMaintainer == null) {
                future.completeExceptionally(new IllegalStateException("GitHub returned no sponsorship data"));
            } else {
                future.complete(result.data.viewer.sponsorshipsAsMaintainer);
            }
        });

        return future.thenCompose(page -> {
            for (SponsorshipsQuery.Node node : page.nodes) {
                Sponsor sponsor = Sponsor.from(node);
                if (sponsor != null) {
                    sponsors.add(sponsor);
                }
            }

            if (Boolean.TRUE.equals(page.pageInfo.hasNextPage)) {
                return getSponsors(page.pageInfo.endCursor, sponsors);
            }

            return CompletableFuture.completedFuture(sponsors);
        });
    }

    /**
     * Convert the old username based links to GitHub account ID based links
     */
    private static void migrateLegacyLinks() {
        Map<Long, String> legacyLinks = DiscordBot.storageManager.getLegacyGithubLinks();
        if (legacyLinks.isEmpty()) {
            return;
        }

        DiscordBot.LOGGER.info("Migrating {} legacy GitHub links", legacyLinks.size());

        boolean failed = false;
        for (Map.Entry<Long, String> entry : legacyLinks.entrySet()) {
            long discordId = entry.getKey();
            String username = entry.getValue();

            try {
                long githubId = DiscordBot.getGithub().getUser(username).getId();

                if (DiscordBot.storageManager.getGithubLink(discordId) != null || DiscordBot.storageManager.getGithubLinkByGithubId(githubId) != null) {
                    DiscordBot.LOGGER.warn("Skipping legacy GitHub link {} -> {} as it is already linked", discordId, username);
                    continue;
                }

                DiscordBot.storageManager.setGithubLink(discordId, githubId, username);
            } catch (GHFileNotFoundException e) {
                DiscordBot.LOGGER.warn("Skipping legacy GitHub link {} -> {} as the account no longer exists", discordId, username);
            } catch (IOException e) {
                DiscordBot.LOGGER.error("Failed to migrate legacy GitHub link {} -> {}", discordId, username, e);
                failed = true;
            }
        }

        // Keep the legacy links so they are retried on the next start
        if (failed) {
            return;
        }

        DiscordBot.storageManager.dropLegacyGithubLinks();
        DiscordBot.LOGGER.info("Finished migrating legacy GitHub links");
    }

    private static synchronized void sync() {
        Map<Long, Sponsor> active;
        try {
            active = getActiveSponsors().join();
        } catch (Exception e) {
            DiscordBot.LOGGER.error("Failed to fetch sponsors, skipping sponsor sync", e);
            return;
        }

        if (lastSponsors != null) {
            announceChanges(active);
        }
        lastSponsors = active;

        List<GithubLink> links = DiscordBot.storageManager.getGithubLinks();
        for (Role role : getDonatorRoles()) {
            updateRoles(role, links, active);
        }
    }

    private static void announceChanges(Map<Long, Sponsor> active) {
        List<MessageEmbed> embeds = new ArrayList<>();

        for (Sponsor sponsor : active.values()) {
            if (!lastSponsors.containsKey(sponsor.githubId())) {
                embeds.add(sponsor.toEmbed());
            }
        }

        for (Sponsor sponsor : lastSponsors.values()) {
            if (!active.containsKey(sponsor.githubId())) {
                embeds.add(sponsor.toStoppedEmbed());
            }
        }

        if (embeds.isEmpty()) {
            return;
        }

        for (Guild guild : DiscordBot.getJDA().getGuilds()) {
            TextChannel channel = ServerSettings.getDonationFeedsChannel(guild);
            if (channel != null) {
                for (MessageEmbed embed : embeds) {
                    channel.sendMessageEmbeds(embed).queue();
                }
            }
        }
    }

    private static void updateRoles(Role role, List<GithubLink> links, Map<Long, Sponsor> active) {
        Guild guild = role.getGuild();

        for (GithubLink link : links) {
            Member member = guild.getMemberById(link.discordId());
            if (member == null) {
                continue;
            }

            Sponsor sponsor = active.get(link.githubId());
            boolean eligible = sponsor != null && sponsor.eligible();
            boolean hasRole = member.getRoles().contains(role);

            if (eligible && !hasRole) {
                guild.addRoleToMember(member, role).queue();
                sendDM(member, "Thank you for sponsoring me. You have been given the " + role.getName() + " role in the " + guild.getName() + " Discord server!");
            } else if (!eligible && hasRole) {
                guild.removeRoleFromMember(member, role).queue();
                sendDM(member, "Your sponsorship has expired. You have been removed from the " + role.getName() + " role in the " + guild.getName() + " Discord server! If you sponsor again at <" + SPONSOR_LINK + "> it will be given back automatically.");
            }
        }
    }

    private static List<Role> getDonatorRoles() {
        List<Role> roles = new ArrayList<>();
        for (Guild guild : DiscordBot.getJDA().getGuilds()) {
            Role role = ServerSettings.getDonatorRole(guild);
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    private static void sendDM(Member member, String message) {
        member.getUser().openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(message))
                .queue(null, throwable -> DiscordBot.LOGGER.debug("Unable to DM {}", member.getUser().getName()));
    }

    /**
     * Start a verification, replacing any pending one for the user
     *
     * @param discordId The Discord user ID
     * @return The GitHub OAuth link for the user to open
     */
    public static String createVerification(long discordId) {
        linkMap.asMap().values().removeIf(id -> id == discordId);

        UUID state = UUID.randomUUID();
        linkMap.put(state, discordId);
        return "https://github.com/login/oauth/authorize?client_id=" + PropertiesManager.getGithubClientId() + "&state=" + state;
    }

    /**
     * Link a user's GitHub account from the OAuth callback and give them the donator role if eligible
     *
     * @param state The state from the OAuth link
     * @param code The OAuth code from GitHub
     * @return The result to show to the user
     */
    public static VerificationResult verify(UUID state, String code) {
        Long discordId = linkMap.getIfPresent(state);
        if (discordId == null) {
            return VerificationResult.error("Your verification request has expired. Please run /verify again.");
        }
        linkMap.invalidate(state);

        JSONObject user = getGithubUser(code);
        if (user == null) {
            return VerificationResult.error("Could not fetch your GitHub account. Please run /verify again.");
        }
        long githubId = user.getLong("id");
        String githubLogin = user.getString("login");

        List<Role> roles = getDonatorRoles().stream()
                .filter(role -> role.getGuild().getMemberById(discordId) != null)
                .toList();
        if (roles.isEmpty()) {
            return VerificationResult.error("You need to be in a Discord server with a donator role to verify.");
        }

        GithubLink existing = DiscordBot.storageManager.getGithubLinkByGithubId(githubId);
        if (existing != null && existing.discordId() != discordId) {
            return VerificationResult.error("The GitHub account " + githubLogin + " is already linked to a different Discord account. Run /unverify on that account first.");
        }

        DiscordBot.storageManager.setGithubLink(discordId, githubId, githubLogin);

        String linked = "Linked your GitHub account " + githubLogin;

        Sponsor sponsor;
        try {
            sponsor = getActiveSponsors().join().get(githubId);
        } catch (Exception e) {
            DiscordBot.LOGGER.error("Failed to fetch sponsors for verification", e);
            return VerificationResult.success(linked + ", but your sponsorship couldn't be checked right now. If you are sponsoring, the role will be given automatically within 30 minutes.");
        }

        if (sponsor == null) {
            return VerificationResult.success(linked + ", but no active sponsorship was found. Once you sponsor at <a href=\"" + SPONSOR_LINK + "\">" + SPONSOR_LINK + "</a> the role will be given automatically within 30 minutes.");
        }

        if (!sponsor.eligible()) {
            return VerificationResult.success(linked + ", but your sponsorship of $" + String.format("%.02f", sponsor.amount()) + " is below the $" + String.format("%.02f", DONATE_MIN) + " minimum for the donator role.");
        }

        for (Role role : roles) {
            Member member = role.getGuild().getMemberById(discordId);
            if (member != null && !member.getRoles().contains(role)) {
                role.getGuild().addRoleToMember(member, role).queue();
            }
        }

        return VerificationResult.success("Thank you for sponsoring! " + linked + " and gave you the donator role. You can now close this window.");
    }

    /**
     * Exchange an OAuth code for the user's GitHub account
     *
     * @param code The OAuth code from GitHub
     * @return The GitHub user or null if it couldn't be fetched
     */
    private static JSONObject getGithubUser(String code) {
        try {
            JSONObject body = new JSONObject();
            body.put("client_id", PropertiesManager.getGithubClientId());
            body.put("client_secret", PropertiesManager.getGithubClientSecret());
            body.put("code", code);

            String accessToken = RestClient.post("https://github.com/login/oauth/access_token", body, "Accept: application/json").asJSONObject().optString("access_token", null);
            if (accessToken == null) {
                return null;
            }

            RestClient.Response response = RestClient.get("https://api.github.com/user", "Authorization: Bearer " + accessToken, "Accept: application/vnd.github+json");
            return response.success() ? response.asJSONObject() : null;
        } catch (Exception e) {
            DiscordBot.LOGGER.error("Failed to fetch GitHub account for verification", e);
            return null;
        }
    }

    /**
     * Unlink a user's GitHub account and remove their donator roles
     *
     * @param discordId The Discord user ID
     * @return If the user had a linked account
     */
    public static boolean unverify(long discordId) {
        if (!DiscordBot.storageManager.removeGithubLink(discordId)) {
            return false;
        }

        for (Role role : getDonatorRoles()) {
            Member member = role.getGuild().getMemberById(discordId);
            if (member != null && member.getRoles().contains(role)) {
                role.getGuild().removeRoleFromMember(member, role).queue();
            }
        }

        return true;
    }

    public record VerificationResult(boolean success, String title, String message) {
        public static VerificationResult success(String message) {
            return new VerificationResult(true, "Your GitHub account has been linked", message);
        }

        public static VerificationResult error(String message) {
            return new VerificationResult(false, "Verification was not successful", message);
        }
    }
}
