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

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.rtm516.discordbot.util.BotColors;
import org.rtm516.discordbot.util.Sponsor;
import org.rtm516.discordbot.util.SponsorUtil;

import java.util.Optional;

public class CheckDonateCommand extends SlashCommand {
    public CheckDonateCommand() {
        this.name = "checkdonate";
        this.help = "Checks if your linked github account has an active sponsorship";
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        InteractionHook interactionHook = event.deferReply(true).complete();

        // Check if the user has linked their github account
        String ghUsername = SponsorUtil.getGithub(event.getUser());
        if (ghUsername == null) {
            interactionHook.editOriginalEmbeds(new EmbedBuilder()
                    .setTitle("No linked github account found")
                    .setDescription("Please link your github account on " + SponsorUtil.getGithubLink(event.getUser()))
                    .setColor(BotColors.FAILURE.getColor())
                    .build()).queue();
            return;
        }

        // Check if the user has an active sponsorship
        SponsorUtil.getSponsors().thenAccept(sponsors -> {
            Optional<Sponsor> foundSponsor = sponsors.stream().filter(sponsor -> sponsor.username().equals(ghUsername)).findFirst();

            if (foundSponsor.isPresent() && foundSponsor.get().active()) {
                interactionHook.editOriginalEmbeds(new EmbedBuilder()
                        .setTitle("Active Sponsorship Found!")
                        .addField("Github Username", foundSponsor.get().username(), true)
                        .addField("Amount", "$" + String.format("%.02f", foundSponsor.get().amount()), true)
                        .addField("Re-occuring", String.valueOf(!foundSponsor.get().oneTime()), true)
                        .addField("Started", TimeFormat.DATE_TIME_SHORT.format(foundSponsor.get().started()), true)
                        .setColor(BotColors.SUCCESS.getColor())
                        .build()).queue();

                SponsorUtil.checkAdd(event.getGuild(), foundSponsor.get());
            } else {
                interactionHook.editOriginalEmbeds(new EmbedBuilder()
                        .setTitle("No active sponsorship found")
                        .addField("Github Username", ghUsername, true)
                        .setColor(BotColors.FAILURE.getColor())
                        .build()).queue();
            }
        });
    }
}
