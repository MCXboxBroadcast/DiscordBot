/*
 * Copyright (c) 2020-2022 GeyserMC. http://geysermc.org
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

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.rtm516.nethernettester.NetherNetTester;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.rtm516.discordbot.DiscordBot;
import org.rtm516.discordbot.util.BotColors;
import org.rtm516.discordbot.util.TesterLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestServerCommand extends SlashCommand {
    private static Logger LOGGER = LoggerFactory.getLogger(TestServerCommand.class);

    public TestServerCommand() {
        this.name = "testserver";
        this.arguments = "[gamertag]";
        this.help = "Tests an instance of MCXboxBroadcast attached to a given gamertag";
        this.options = List.of(
                new OptionData(OptionType.STRING, "gamertag", "The gamertag to test", true)
        );

        this.userPermissions = new Permission[] { Permission.MESSAGE_MANAGE };
        this.botPermissions = new Permission[] { Permission.MESSAGE_MANAGE };
    }

    @Override
    protected void execute(CommandEvent event) {
        String gamertag = event.getArgs().trim();
        if (gamertag.isEmpty()) {
            event.replyError("You must provide a gamertag.");
            return;
        }

        event.getMessage().replyComponents(buildContainer(gamertag, new ArrayList<>(), false, false))
                .useComponentsV2()
                .queue(message -> {
                    runTest(gamertag, container ->
                            message.editMessageComponents(container).useComponentsV2().queue()
                    );
                });
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String gamertag = event.optString("gamertag", "");
        if (gamertag.isEmpty()) {
            event.reply("You must provide a gamertag.").setEphemeral(true).queue();
            return;
        }

        event.replyComponents(buildContainer(gamertag, new ArrayList<>(), false, false))
                .useComponentsV2()
                .queue(hook -> {
                    runTest(gamertag, container ->
                            hook.editOriginalComponents(container).useComponentsV2().queue()
                    );
                });
    }

    private Container buildContainer(String gamertag, List<String> log, boolean finished, boolean success) {
        Color color;
        if (finished) {
            color = success ? BotColors.SUCCESS.getColor() : BotColors.FAILURE.getColor();
        } else {
            color = BotColors.NEUTRAL.getColor();
        }

        return Container.of(
                TextDisplay.of("## NetherNet Test"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("**Gamertag:** " + gamertag),
                TextDisplay.of("**Status:** " + (finished ? (success ? "\u2705 Test completed" : "\u274C Test failed") : "\u23F3 Test in progress...")),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("```\n" + (log.size() == 0 ? "Initialising..." : String.join("\n", log)) + "\n```")
        ).withAccentColor(color);
    }

    private void runTest(String gamertag, Consumer<Container> updateMessage) {
        List<String> log = new ArrayList<>();

        NetherNetTester tester = new NetherNetTester()
                .logger(new TesterLogger(LOGGER))
                .scheduledExecutorService(DiscordBot.getGeneralThreadPool())
                .targetGamertag(gamertag)
                .statusCallback(status -> {
                    log.add(status);
                    updateMessage.accept(buildContainer(gamertag, log, false, false));
                });

        tester.start().thenAccept(success -> {
            log.add("Test completed successfully!");
            updateMessage.accept(buildContainer(gamertag, log, true, true));
        }).exceptionally(ex -> {
            String errorMsg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.add("Error: " + errorMsg);
            updateMessage.accept(buildContainer(gamertag, log, true, false));
            return null;
        });
    }
}
