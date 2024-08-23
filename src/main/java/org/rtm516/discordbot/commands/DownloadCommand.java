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

import com.google.common.collect.ImmutableMap;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.rtm516.discordbot.util.BotColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DownloadCommand extends SlashCommand {
    private final DownloadOption defaultDownloadOption;
    private final Map<String, DownloadOption> optionsToRepository;

    public DownloadCommand() {
        this.name = "download";
        this.arguments = "[program]";
        this.help = "Sends a link to download the latest version of a program";
        this.guildOnly = false;

        this.defaultDownloadOption = new DownloadOption("MCXboxBroadcast", "https://github.com/MCXboxBroadcast/Broadcaster/releases", "https://github.com/MCXboxBroadcast.png");
        this.optionsToRepository = ImmutableMap.<String, DownloadOption>builder()
                .put("mcxboxbroadcast", this.defaultDownloadOption)
                .put("manager", new DownloadOption("MCXboxBroadcast Manager", "https://github.com/MCXboxBroadcast/Manager/releases", "https://github.com/MCXboxBroadcast.png"))
                .put("archiver", new DownloadOption("Minecraft Bedrock Archiver", "https://github.com/MinecraftBedrockArchiver/Archiver", "https://github.com/MinecraftBedrockArchiver.png"))
                .build();

        List<Command.Choice> choices = new ArrayList<>();
        for (Map.Entry<String, DownloadOption> entry: this.optionsToRepository.entrySet()) {
            choices.add(new Command.Choice(entry.getValue().friendlyName, entry.getKey()));
        }

        this.options = Collections.singletonList( // Future thing: add branch??
                new OptionData(OptionType.STRING, "program", "The program to download from.").addChoices(choices)
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        List<String> args = new ArrayList<>(Arrays.asList(event.getArgs().split(" ")));

        String program = "mcxboxbroadcast";
        if (!args.get(0).isEmpty()) {
            program = args.get(0);
        }

        DownloadOption downloadOption = optionsToRepository.getOrDefault(program.toLowerCase(Locale.ROOT), this.defaultDownloadOption);

        event.getMessage().replyEmbeds(new EmbedBuilder()
                .setTitle("Download " + downloadOption.friendlyName)
                .setDescription("Download at " + downloadOption.downloadUrl)
                .setThumbnail(downloadOption.imageUrl)
                .setColor(BotColors.SUCCESS.getColor())
                .build()).queue();
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String program = event.optString("program", "mcxboxbroadcast");

        DownloadOption downloadOption = optionsToRepository.getOrDefault(program.toLowerCase(Locale.ROOT), this.defaultDownloadOption);

        event.replyEmbeds(new EmbedBuilder()
                .setTitle("Download " + downloadOption.friendlyName)
                .setDescription("Download at " + downloadOption.downloadUrl)
                .setThumbnail(downloadOption.imageUrl)
                .setColor(BotColors.SUCCESS.getColor())
                .build()).queue();
    }

    private static class DownloadOption {
        private final String friendlyName;
        private final String downloadUrl;
        private final String imageUrl;

        public DownloadOption(String friendlyName, String downloadUrl, String imageUrl) {
            this.friendlyName = friendlyName;
            this.downloadUrl = downloadUrl;
            this.imageUrl = imageUrl;
        }
    }
}
