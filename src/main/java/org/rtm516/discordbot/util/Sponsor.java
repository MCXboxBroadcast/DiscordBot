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

import com.rtm516.discordbot.graphql.SponsorshipsQuery;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;

/**
 * A sponsorship to the bot's GitHub account
 *
 * @param githubId The sponsor's GitHub account ID
 * @param username The sponsor's GitHub username
 * @param amount The sponsorship amount in dollars
 * @param oneTime If the sponsorship was a one-time payment
 * @param githubActive If GitHub reports the sponsorship as active
 * @param started When the sponsorship or its current tier started
 */
public record Sponsor(
        long githubId,
        String username,
        float amount,
        boolean oneTime,
        boolean githubActive,
        Instant started
) {
    public static Sponsor from(SponsorshipsQuery.Node node) {
        SponsorshipsQuery.SponsorEntity entity = node.sponsorEntity;
        if (entity == null) {
            return null;
        }

        Integer githubId;
        String username;
        if (entity.onUser != null) {
            githubId = entity.onUser.databaseId;
            username = entity.onUser.login;
        } else if (entity.onOrganization != null) {
            githubId = entity.onOrganization.databaseId;
            username = entity.onOrganization.login;
        } else {
            return null;
        }

        if (githubId == null) {
            return null;
        }

        Instant started = Instant.parse((String) node.createdAt);
        if (node.tierSelectedAt != null) {
            Instant tierSelectedAt = Instant.parse((String) node.tierSelectedAt);
            if (tierSelectedAt.isAfter(started)) {
                started = tierSelectedAt;
            }
        }

        boolean oneTime = Boolean.TRUE.equals(node.isOneTimePayment) || (node.tier != null && Boolean.TRUE.equals(node.tier.isOneTime));
        float amount = node.tier != null && node.tier.monthlyPriceInCents != null ? node.tier.monthlyPriceInCents / 100f : 0f;

        return new Sponsor(githubId, username, amount, oneTime, Boolean.TRUE.equals(node.isActive), started);
    }

    /**
     * One-time sponsorships are active for {@link SponsorUtil#ONE_TIME_DURATION}, monthly ones until cancelled
     *
     * @return If the sponsorship is active
     */
    public boolean active() {
        if (oneTime) {
            return started.plus(SponsorUtil.ONE_TIME_DURATION).isAfter(Instant.now());
        }
        return githubActive;
    }

    /**
     * @return If the sponsorship should be given the donator role
     */
    public boolean eligible() {
        return active() && amount >= SponsorUtil.DONATE_MIN;
    }

    public String toString() {
        return username + " sponsored you for $" + String.format("%.02f", amount) + " on " + started + (oneTime ? " (one-time)" : "");
    }

    public MessageEmbed toEmbed() {
        return new EmbedBuilder()
                .setAuthor(username, "https://github.com/" + username, "https://github.com/" + username + ".png")
                .setDescription("Sponsored you for $" + String.format("%.02f", amount) + (oneTime ? " (one-time)" : ""))
                .setTimestamp(started)
                .setColor(BotColors.SUCCESS.getColor())
                .build();
    }

    public MessageEmbed toStoppedEmbed() {
        return new EmbedBuilder()
                .setAuthor(username, "https://github.com/" + username, "https://github.com/" + username + ".png")
                .setDescription("Stopped sponsoring you")
                .setColor(BotColors.FAILURE.getColor())
                .build();
    }
}
