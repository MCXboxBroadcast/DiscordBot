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

package org.rtm516.discordbot.commands;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import org.rtm516.discordbot.DiscordBot;
import org.rtm516.discordbot.storage.GithubLink;
import org.rtm516.discordbot.storage.ServerSettings;
import org.rtm516.discordbot.util.BotColors;
import org.rtm516.discordbot.util.SponsorUtil;

import java.util.concurrent.TimeUnit;

public class VerifyCommand extends SlashCommand {
    private final Cache<Long, Boolean> recentlySent = CacheBuilder.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();

    public VerifyCommand() {
        this.name = "verify";
        this.help = "Link your GitHub account to get the donator role for sponsoring";
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        long id = event.getUser().getIdLong();

        if (recentlySent.getIfPresent(id) != null) {
            reply(event, new EmbedBuilder()
                    .setTitle("Please wait")
                    .setDescription("Please wait 2 minutes before trying to verify again.")
                    .setColor(BotColors.FAILURE.getColor()));
            return;
        }

        Role role = ServerSettings.getDonatorRole(event.getGuild());
        if (role == null) {
            reply(event, new EmbedBuilder()
                    .setTitle("Not available")
                    .setDescription("This server doesn't have a donator role set up.")
                    .setColor(BotColors.FAILURE.getColor()));
            return;
        }

        GithubLink link = DiscordBot.storageManager.getGithubLink(id);
        if (link != null && event.getMember().getRoles().contains(role)) {
            reply(event, new EmbedBuilder()
                    .setTitle("Already verified")
                    .setDescription("Your GitHub account [" + link.githubLogin() + "](https://github.com/" + link.githubLogin() + ") is linked and you have the " + role.getAsMention() + " role, thank you for sponsoring!")
                    .setColor(BotColors.SUCCESS.getColor()));
            return;
        }

        recentlySent.put(id, Boolean.TRUE);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Verify your GitHub sponsorship")
                .setDescription("If you sponsor me on [GitHub Sponsors](" + SponsorUtil.SPONSOR_LINK + ") for $" + String.format("%.0f", SponsorUtil.DONATE_MIN) + " or more, [click here](" + SponsorUtil.createVerification(id) + ") to link your GitHub account and get the " + role.getAsMention() + " role.")
                .setFooter("The link expires in 5 minutes")
                .setColor(BotColors.NEUTRAL.getColor());

        if (link != null) {
            embed.addField("Linked GitHub account", "[" + link.githubLogin() + "](https://github.com/" + link.githubLogin() + ")\nThe role is given automatically within 30 minutes of sponsoring, or use the link above to check now.", false);
        }

        if (ServerSettings.getManualDonators(event.getGuild()).contains(id)) {
            embed.addField("Manually flagged", "You are manually flagged as a donator, so linking your GitHub account won't make a difference to your role.", false);
        }

        reply(event, embed);
    }

    private void reply(SlashCommandEvent event, EmbedBuilder embed) {
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
