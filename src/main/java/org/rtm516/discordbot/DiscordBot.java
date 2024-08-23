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

package org.rtm516.discordbot;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.ContextMenu;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.rtm516.discordbot.listeners.*;
import org.rtm516.discordbot.storage.AbstractStorageManager;
import org.rtm516.discordbot.storage.StorageType;
import org.rtm516.discordbot.tags.TagsListener;
import org.rtm516.discordbot.tags.TagsManager;
import org.rtm516.discordbot.util.PropertiesManager;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class DiscordBot {
    // Instance Variables
    public static final Logger LOGGER = LoggerFactory.getLogger(DiscordBot.class);
    public static final List<Command> COMMANDS;
    public static final List<SlashCommand> SLASH_COMMANDS;
    public static final List<ContextMenu> CONTEXT_MENUS;

    public static AbstractStorageManager storageManager;

    private static ScheduledExecutorService generalThreadPool;

    private static JDA jda;
    private static GitHub github;

    static {
        // Gathers all commands from "commands" package.
        List<Command> commands = new ArrayList<>();
        List<SlashCommand> slashCommands = new ArrayList<>();
        try {
            Reflections reflections = new Reflections("org.rtm516.discordbot.commands");
            Set<Class<? extends Command>> subTypes = reflections.getSubTypesOf(Command.class);
            for (Class<? extends Command> theClass : subTypes) {
                // Don't load SubCommands
                if (theClass.getName().contains("SubCommand")) {
                    continue;
                }
                try {
                    commands.add(theClass.getDeclaredConstructor().newInstance());
                    LoggerFactory.getLogger(theClass).debug("Loaded Command Successfully!");
                } catch (InstantiationException e) {
                    // Safe to ignore, we probably tried to load a Slash Command
                }
            }

            Set<Class<? extends SlashCommand>> slashSubTypes = reflections.getSubTypesOf(SlashCommand.class);
            for (Class<? extends SlashCommand> theClass : slashSubTypes) {
                // Don't load SubCommands
                if (theClass.getName().contains("SubCommand")) {
                    continue;
                }
                slashCommands.add(theClass.getDeclaredConstructor().newInstance());
                LoggerFactory.getLogger(theClass).debug("Loaded SlashCommand Successfully!");
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Unable to load commands", e);
        }
        COMMANDS = commands;
        SLASH_COMMANDS = slashCommands;

        // Gathers all context menu items from "context_menu" package.
        List<ContextMenu> contextMenus = new ArrayList<>();
        try {
            Reflections reflections = new Reflections("org.rtm516.discordbot.context_menus");
            Set<Class<? extends ContextMenu>> subTypes = reflections.getSubTypesOf(ContextMenu.class);
            for (Class<? extends ContextMenu> theClass : subTypes) {
                if (!theClass.getPackageName().startsWith("org.rtm516.discordbot.context_menus")) {
                    continue;
                }
                contextMenus.add(theClass.getDeclaredConstructor().newInstance());
                LoggerFactory.getLogger(theClass).debug("Loaded ContextMenu Successfully!");
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Unable to load context menus", e);
        }
        CONTEXT_MENUS = contextMenus;
    }

    public static void main(String[] args) throws IOException {
        // Load properties into the PropertiesManager
        Properties prop = new Properties();
        prop.load(new FileInputStream("bot.properties"));
        PropertiesManager.loadProperties(prop);

        // Connect to github
        github = new GitHubBuilder().withOAuthToken(PropertiesManager.getGithubToken()).build();

        // Initialize the waiter
        EventWaiter waiter = new EventWaiter();

        // Load filters
        SwearHandler.loadFilters();

        // Load the db
        StorageType storageType = StorageType.getByName(PropertiesManager.getDatabaseType());
        if (storageType == StorageType.UNKNOWN) {
            LOGGER.error("Invalid database type! '" + PropertiesManager.getDatabaseType() + "'");
            System.exit(1);
        }

        try {
            storageManager = storageType.getStorageManager().getDeclaredConstructor().newInstance();
            storageManager.setupStorage();
        } catch (Exception e) {
            LOGGER.error("Unable to create database link!", e);
            System.exit(1);
        }

        // Setup the main client
        CommandClientBuilder client = new CommandClientBuilder();
        client.setActivity(null);
        client.setOwnerId("0"); // No owner
        client.setPrefix(PropertiesManager.getPrefix());
        client.useHelpBuilder(false);
        client.addCommands(COMMANDS.toArray(new Command[0]));
        client.addSlashCommands(SLASH_COMMANDS.toArray(new SlashCommand[0]));
        client.addContextMenus(CONTEXT_MENUS.toArray(new ContextMenu[0]));
        client.setListener(new CommandErrorHandler());
        client.setCommandPreProcessBiFunction((event, command) -> !SwearHandler.filteredMessages.contains(event.getMessage().getIdLong()));

        // Setup the tag client
        CommandClientBuilder tagClient = new CommandClientBuilder();
        tagClient.setActivity(null);
        tagClient.setOwnerId("0"); // No owner
        String tagPrefix = PropertiesManager.getPrefix() + PropertiesManager.getPrefix();
        tagClient.setPrefix(tagPrefix);
        tagClient.setPrefixes(new String[] {"!tag "});
        tagClient.useHelpBuilder(false);
        tagClient.addCommands(TagsManager.getTags().toArray(new Command[0]));
        tagClient.setListener(new TagsListener());
        tagClient.setCommandPreProcessBiFunction((event, command) -> !SwearHandler.filteredMessages.contains(event.getMessage().getIdLong()));
        tagClient.setManualUpsert(true);

        // Disable pings on replies
        MessageRequest.setDefaultMentionRepliedUser(false);

        // Setup the thread pool
        generalThreadPool = Executors.newScheduledThreadPool(5);

        // Register JDA
        try {
            jda = JDABuilder.createDefault(PropertiesManager.getToken())
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES, GatewayIntent.MESSAGE_CONTENT)
                    .enableCache(CacheFlag.ACTIVITY)
                    .enableCache(CacheFlag.ROLE_TAGS)
                    .setStatus(OnlineStatus.ONLINE)
                    .setEnableShutdownHook(true)
                    .addEventListeners(waiter,
                            new LogHandler(),
                            new SwearHandler(),
                            new PersistentRoleHandler(),
                            new FileHandler(),
                            new ShutdownHandler(),
                            new BadLinksHandler(),
                            new HelpHandler(),
                            new DeleteHandler(),
                            new AutoModHandler(),
                            client.build(),
                            tagClient.build())
                    .build();
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Failed to initialize JDA!", exception);
            System.exit(1);
        }

        // Register listeners
        jda.addEventListener();
    }

    public static JDA getJDA() {
        return jda;
    }

    public static GitHub getGithub() {
        return github;
    }

    public static ScheduledExecutorService getGeneralThreadPool() {
        return generalThreadPool;
    }

    public static void shutdown() {
        DiscordBot.LOGGER.info("Shutting down storage...");
        storageManager.closeStorage();
        DiscordBot.LOGGER.info("Shutting down thread pool...");
        generalThreadPool.shutdown();
        DiscordBot.LOGGER.info("Finished shutdown, exiting!");
        System.exit(0);
    }
}
