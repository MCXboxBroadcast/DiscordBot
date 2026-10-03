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

package org.rtm516.discordbot.commands.administration;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.rtm516.discordbot.DiscordBot;
import org.rtm516.discordbot.storage.GithubLink;
import org.rtm516.discordbot.storage.ServerSettings;
import org.rtm516.discordbot.util.BotColors;
import org.rtm516.discordbot.util.Sponsor;
import org.rtm516.discordbot.util.SponsorUtil;
import org.rtm516.discordbot.util.SponsorUtil.DonatorStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class DonatorsCommand extends SlashCommand {
    private static final Permission[] PERMISSIONS = new Permission[] { Permission.MANAGE_ROLES };

    public DonatorsCommand() {
        this.name = "donators";
        this.help = "Manage and audit who has the donator role";
        this.hidden = true;
        this.guildOnly = true;
        this.userPermissions = PERMISSIONS;

        this.children = new SlashCommand[] {
                new FlagSubCommand(),
                new UnflagSubCommand(),
                new AuditSubCommand(),
                new CleanupSubCommand()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        // Unused as this command only has subcommands
    }

    public static class FlagSubCommand extends SlashCommand {
        public FlagSubCommand() {
            this.name = "flag";
            this.help = "Flag a user as a manual donator and give them the donator role";
            this.userPermissions = PERMISSIONS;
            this.botPermissions = PERMISSIONS;
            this.options = Collections.singletonList(
                    new OptionData(OptionType.USER, "user", "The user to flag", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            Guild guild = event.getGuild();
            Role role = ServerSettings.getDonatorRole(guild);
            if (role == null) {
                reply(event, notSetUp());
                return;
            }

            Member member = event.optMember("user");
            if (member == null) {
                reply(event, embed("Invalid user", "That user isn't in this server.", BotColors.FAILURE));
                return;
            }

            Set<Long> manualDonators = ServerSettings.getManualDonators(guild);
            if (!manualDonators.add(member.getIdLong())) {
                reply(event, embed("Already flagged", member.getAsMention() + " is already flagged as a manual donator.", BotColors.FAILURE));
                return;
            }
            ServerSettings.setManualDonators(guild, manualDonators);
            postToFeed(guild, member.getUser(), "Flagged as a manual donator by " + event.getUser().getAsMention(), BotColors.SUCCESS);

            if (!member.getRoles().contains(role)) {
                guild.addRoleToMember(member, role).queue();
            }

            reply(event, embed("Flagged manual donator", member.getAsMention() + " has been flagged as a manual donator and has the " + role.getAsMention() + " role.", BotColors.SUCCESS));
        }
    }

    public static class UnflagSubCommand extends SlashCommand {
        public UnflagSubCommand() {
            this.name = "unflag";
            this.help = "Remove a user's manual donator flag and the donator role if they aren't a sponsor";
            this.userPermissions = PERMISSIONS;
            this.botPermissions = PERMISSIONS;
            this.options = Collections.singletonList(
                    new OptionData(OptionType.USER, "user", "The user to unflag", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            Guild guild = event.getGuild();
            User user = event.optUser("user");

            Set<Long> manualDonators = ServerSettings.getManualDonators(guild);
            if (!manualDonators.remove(user.getIdLong())) {
                reply(event, embed("Not flagged", user.getAsMention() + " isn't flagged as a manual donator.", BotColors.FAILURE));
                return;
            }
            ServerSettings.setManualDonators(guild, manualDonators);
            postToFeed(guild, user, "Unflagged as a manual donator by " + event.getUser().getAsMention(), BotColors.FAILURE);

            String title = "Unflagged manual donator";
            String unflagged = user.getAsMention() + " is no longer flagged as a manual donator";

            Role role = ServerSettings.getDonatorRole(guild);
            Member member = guild.getMemberById(user.getIdLong());
            if (role == null || member == null || !member.getRoles().contains(role)) {
                reply(event, embed(title, unflagged + ".", BotColors.SUCCESS));
                return;
            }

            InteractionHook hook = event.deferReply(true).complete();

            SponsorUtil.getActiveSponsors().whenComplete((active, throwable) -> {
                if (throwable != null) {
                    hook.editOriginalEmbeds(embed(title, unflagged + ", but GitHub couldn't be checked so they still have the role. Run `/donators cleanup` later to remove it.", BotColors.WARNING)).queue();
                    return;
                }

                GithubLink link = DiscordBot.storageManager.getGithubLink(user.getIdLong());
                if (SponsorUtil.getStatus(user.getIdLong(), link, manualDonators, active).shouldHaveRole()) {
                    hook.editOriginalEmbeds(embed(title, unflagged + ", but keeps the role as an active GitHub sponsor.", BotColors.SUCCESS)).queue();
                    return;
                }

                guild.removeRoleFromMember(member, role).queue();
                hook.editOriginalEmbeds(embed(title, unflagged + " and the " + role.getAsMention() + " role has been removed.", BotColors.SUCCESS)).queue();
            });
        }
    }

    public static class AuditSubCommand extends SlashCommand {
        public AuditSubCommand() {
            this.name = "audit";
            this.help = "List who has the donator role and if they should";
            this.userPermissions = PERMISSIONS;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            runAudit(event, (hook, audit) -> {
                List<Member> toRemove = audit.toRemove();
                int toFix = toRemove.size() + audit.missingRole().size();

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Donator audit")
                        .setDescription(audit.withRoleCount() + " members have the " + audit.role().getAsMention() + " role. " + (toFix == 0 ? "Everything is up to date." : toFix + " need fixing, run `/donators cleanup` to fix them."))
                        .setColor(toFix == 0 ? BotColors.SUCCESS.getColor() : BotColors.WARNING.getColor());

                for (DonatorStatus status : DonatorStatus.values()) {
                    List<Member> members = audit.withRole().get(status);
                    embed.addField(statusName(status) + " (" + members.size() + ")", formatMembers(members, audit.links()), false);
                }
                embed.addField("Should have the role but don't (" + audit.missingRole().size() + ")", formatMembers(audit.missingRole(), audit.links()), false);

                hook.editOriginalEmbeds(embed.build()).queue();
            });
        }
    }

    public static class CleanupSubCommand extends SlashCommand {
        public CleanupSubCommand() {
            this.name = "cleanup";
            this.help = "Fix who has the donator role based on the audit and message them";
            this.userPermissions = PERMISSIONS;
            this.botPermissions = PERMISSIONS;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            runAudit(event, (hook, audit) -> {
                Role role = audit.role();
                Guild guild = role.getGuild();
                List<Member> toRemove = audit.toRemove();

                for (Member member : toRemove) {
                    guild.removeRoleFromMember(member, role).queue();
                    SponsorUtil.notifyRoleRemoved(member, role, audit.links().containsKey(member.getIdLong()));
                }
                for (Member member : audit.missingRole()) {
                    guild.addRoleToMember(member, role).queue();
                    SponsorUtil.notifyRoleAdded(member, role);
                }

                hook.editOriginalEmbeds(new EmbedBuilder()
                        .setTitle("Donator cleanup")
                        .setDescription("Updated the " + role.getAsMention() + " role.")
                        .addField("Removed from (" + toRemove.size() + ")", formatMembers(toRemove, audit.links()), false)
                        .addField("Given to (" + audit.missingRole().size() + ")", formatMembers(audit.missingRole(), audit.links()), false)
                        .setColor(BotColors.SUCCESS.getColor())
                        .build()).queue();
            });
        }
    }

    /**
     * Fetch the active sponsors and audit the donator role, replying with an error if either isn't possible
     *
     * @param event The command event
     * @param consumer Called with the deferred reply and the audit
     */
    private static void runAudit(SlashCommandEvent event, BiConsumer<InteractionHook, Audit> consumer) {
        Role role = ServerSettings.getDonatorRole(event.getGuild());
        if (role == null) {
            reply(event, notSetUp());
            return;
        }

        InteractionHook hook = event.deferReply(true).complete();

        SponsorUtil.getActiveSponsors().whenComplete((active, throwable) -> {
            if (throwable != null) {
                hook.editOriginalEmbeds(embed("GitHub error", "Couldn't fetch sponsors from GitHub, nothing was changed. Please try again later.", BotColors.FAILURE)).queue();
                return;
            }

            consumer.accept(hook, Audit.of(role, active));
        });
    }

    /**
     * Who has the donator role grouped by their status, and who should have it but doesn't
     */
    private record Audit(Role role, Map<DonatorStatus, List<Member>> withRole, List<Member> missingRole, Map<Long, GithubLink> links) {
        static Audit of(Role role, Map<Long, Sponsor> active) {
            Guild guild = role.getGuild();
            Set<Long> manualDonators = ServerSettings.getManualDonators(guild);

            Map<Long, GithubLink> links = new HashMap<>();
            for (GithubLink link : DiscordBot.storageManager.getGithubLinks()) {
                links.put(link.discordId(), link);
            }

            Map<DonatorStatus, List<Member>> withRole = new EnumMap<>(DonatorStatus.class);
            for (DonatorStatus status : DonatorStatus.values()) {
                withRole.put(status, new ArrayList<>());
            }
            for (Member member : guild.getMembersWithRoles(role)) {
                withRole.get(SponsorUtil.getStatus(member.getIdLong(), links.get(member.getIdLong()), manualDonators, active)).add(member);
            }

            Set<Long> candidates = new HashSet<>(manualDonators);
            candidates.addAll(links.keySet());

            List<Member> missingRole = new ArrayList<>();
            for (long id : candidates) {
                Member member = guild.getMemberById(id);
                if (member != null && !member.getRoles().contains(role) && SponsorUtil.getStatus(id, links.get(id), manualDonators, active).shouldHaveRole()) {
                    missingRole.add(member);
                }
            }

            return new Audit(role, withRole, missingRole, links);
        }

        int withRoleCount() {
            return withRole.values().stream().mapToInt(List::size).sum();
        }

        List<Member> toRemove() {
            List<Member> toRemove = new ArrayList<>();
            for (Map.Entry<DonatorStatus, List<Member>> entry : withRole.entrySet()) {
                if (!entry.getKey().shouldHaveRole()) {
                    toRemove.addAll(entry.getValue());
                }
            }
            return toRemove;
        }
    }

    private static String statusName(DonatorStatus status) {
        return switch (status) {
            case SPONSOR -> "Active GitHub sponsor";
            case MANUAL -> "Manually flagged";
            case NOT_SPONSORING -> "Linked but not sponsoring";
            case NOT_LINKED -> "Not linked or flagged";
        };
    }

    private static String formatMembers(List<Member> members, Map<Long, GithubLink> links) {
        if (members.isEmpty()) {
            return "None";
        }

        StringBuilder value = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            GithubLink link = links.get(member.getIdLong());
            String line = member.getAsMention() + (link != null ? " ([" + link.githubLogin() + "](https://github.com/" + link.githubLogin() + "))" : "") + "\n";

            // Leave space for the "and N more" line
            if (value.length() + line.length() > MessageEmbed.VALUE_MAX_LENGTH - 20) {
                value.append("...and ").append(members.size() - i).append(" more");
                break;
            }
            value.append(line);
        }
        return value.toString();
    }

    private static void postToFeed(Guild guild, User user, String description, BotColors color) {
        TextChannel channel = ServerSettings.getDonationFeedsChannel(guild);
        if (channel == null) {
            return;
        }

        channel.sendMessageEmbeds(new EmbedBuilder()
                .setAuthor(user.getName(), null, user.getEffectiveAvatarUrl())
                .setDescription(user.getAsMention() + " " + description)
                .setColor(color.getColor())
                .build()).queue();
    }

    private static void reply(SlashCommandEvent event, MessageEmbed embed) {
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private static MessageEmbed embed(String title, String description, BotColors color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color.getColor())
                .build();
    }

    private static MessageEmbed notSetUp() {
        return embed("Not available", "This server doesn't have a donator role set up. Set one with `/settings action:set key:donator-role`.", BotColors.FAILURE);
    }
}
