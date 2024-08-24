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

import com.rtm516.discordbot.graphql.SponsorsQuery;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;

public record Sponsor(
        String type,
        String username,
        boolean active,
        boolean oneTime,
        float amount,
        Instant started
) {
    public static Sponsor from(SponsorsQuery.Node node) {
        switch (node.__typename) {
            case "User":
                var userSponsorInfo = node.onUser.sponsorshipsAsSponsor.nodes.get(0);
                return new Sponsor(
                        node.__typename,
                        node.onUser.login,
                        userSponsorInfo.isActive,
                        userSponsorInfo.isOneTimePayment,
                        (userSponsorInfo.tier != null ? userSponsorInfo.tier.monthlyPriceInCents : node.onUser.sponsorshipsAsSponsor.totalRecurringMonthlyPriceInCents)/ 100f,
                        Instant.parse((userSponsorInfo.tierSelectedAt != null ? (String)userSponsorInfo.tierSelectedAt : (String)userSponsorInfo.createdAt))
                );
            case "Organization":
                var orgSponsorInfo = node.onOrganization.sponsorshipsAsSponsor.nodes.get(0);
                return new Sponsor(
                        node.__typename,
                        node.onOrganization.login,
                        orgSponsorInfo.isActive,
                        orgSponsorInfo.isOneTimePayment,
                        (orgSponsorInfo.tier != null ? orgSponsorInfo.tier.monthlyPriceInCents : node.onOrganization.sponsorshipsAsSponsor.totalRecurringMonthlyPriceInCents)/ 100f,
                        Instant.parse((orgSponsorInfo.tierSelectedAt != null ? (String)orgSponsorInfo.tierSelectedAt : (String)orgSponsorInfo.createdAt))
                );
            default:
                return null;
        }
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Sponsor sponsor = (Sponsor) obj;
        return Float.compare(sponsor.amount, amount) == 0 && active == sponsor.active && oneTime == sponsor.oneTime && type.equals(sponsor.type) && username.equals(sponsor.username) && started.equals(sponsor.started);
    }
}
