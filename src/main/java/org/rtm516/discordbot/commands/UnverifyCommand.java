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
import org.rtm516.discordbot.util.BotColors;
import org.rtm516.discordbot.util.SponsorUtil;

public class UnverifyCommand extends SlashCommand {
    public UnverifyCommand() {
        this.name = "unverify";
        this.help = "Unlink your GitHub account and remove the donator role";
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if (SponsorUtil.unverify(event.getUser().getIdLong())) {
            event.replyEmbeds(new EmbedBuilder()
                    .setTitle("GitHub account unlinked")
                    .setDescription("Your GitHub account has been unlinked and the donator role removed.")
                    .setColor(BotColors.SUCCESS.getColor())
                    .build()).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(new EmbedBuilder()
                    .setTitle("No linked GitHub account")
                    .setDescription("You don't have a GitHub account linked.")
                    .setColor(BotColors.FAILURE.getColor())
                    .build()).setEphemeral(true).queue();
        }
    }
}
