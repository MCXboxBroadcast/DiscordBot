/*
 * Copyright (c) 2020-2024 GeyserMC. http://geysermc.org
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

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.rtm516.discordbot.DiscordBot;
import org.rtm516.discordbot.util.BotColors;
import org.rtm516.discordbot.util.PropertiesManager;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Handle the help command
 */
public class HelpCommand extends SlashCommand {

    public HelpCommand() {
        this.name = "help";
        this.help = "I think you already know what this does";
        this.guildOnly = false;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.replyEmbeds(handle("/")).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        event.getMessage().replyEmbeds(handle(PropertiesManager.getPrefix())).queue();
    }

    private MessageEmbed handle(String prefix) {
        EmbedBuilder helpEmbed = new EmbedBuilder()
                .setColor(BotColors.SUCCESS.getColor())
                .setTitle("Geyser Bot Help");

        for (Command command : DiscordBot.COMMANDS.stream().sorted(Comparator.comparing(Command::getName)).collect(Collectors.toList())) {
            if (!command.isHidden()) {
                helpEmbed.addField("`" + prefix + command.getName() + (command.getArguments() != null ? " " + command.getArguments() : "") + "`", command.getHelp(), true);
            }
        }

        return helpEmbed.build();
    }
}
