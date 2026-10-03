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

package org.rtm516.discordbot.storage;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.rtm516.discordbot.util.PropertiesManager;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqliteStorageManager extends MySQLStorageManager {

    @Override
    public void setupStorage() throws Exception {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + PropertiesManager.getDatabase());

        Statement createTables = connection.createStatement();
        createTables.executeUpdate("CREATE TABLE IF NOT EXISTS `preferences` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `server` INTEGER NOT NULL, `key` VARCHAR(32), `value` TEXT NOT NULL, CONSTRAINT `pref_constraint` UNIQUE (`server`,`key`));");
        createTables.executeUpdate("CREATE TABLE IF NOT EXISTS `persistent_roles` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `server` INTEGER NOT NULL, `user` INTEGER NOT NULL, `role` INTEGER NOT NULL, CONSTRAINT `role_constraint` UNIQUE (`server`,`user`,`role`));");
        createTables.executeUpdate("CREATE TABLE IF NOT EXISTS `github_sponsor_links` (`user` INTEGER NOT NULL PRIMARY KEY, `github_id` INTEGER NOT NULL UNIQUE, `github_login` VARCHAR(39) NOT NULL);");
        createTables.close();
    }

    @Override
    public void closeStorage() {
        try {
            connection.close();
        } catch (SQLException ignored) { }
    }

    @Override
    public String getServerPreference(long serverID, String preference) {
        try {
            Statement getPreferenceValue = connection.createStatement();
            ResultSet rs = getPreferenceValue.executeQuery("SELECT `value` FROM `preferences` WHERE `server`=" + serverID + " AND `key`='" + preference + "';");

            if (rs.next()) {
                return rs.getString("value");
            }

            getPreferenceValue.close();
        } catch (SQLException ignored) { }

        return null;
    }

    @Override
    public void setServerPreference(long serverID, String preference, String value) {
        try {
            Statement updatePreferenceValue = connection.createStatement();
            updatePreferenceValue.executeUpdate("INSERT OR REPLACE INTO `preferences` (`server`, `key`, `value`) VALUES ('" + serverID + "', '" + preference + "', '" + value + "');");
            updatePreferenceValue.close();
        } catch (SQLException ignored) { }
    }

    @Override
    public void addPersistentRole(Member member, Role role) {
        try {
            Statement addPersistentRole = connection.createStatement();
            addPersistentRole.executeUpdate("INSERT OR REPLACE INTO `persistent_roles` (`server`, `user`, `role`) VALUES (" + member.getGuild().getId() + ", " + member.getId() + ", " + role.getId() + ");");
            addPersistentRole.close();
        } catch (SQLException ignored) { }
    }

    @Override
    public void removePersistentRole(Member member, Role role) {
        try {
            Statement removePersistentRole = connection.createStatement();
            removePersistentRole.executeUpdate("DELETE FROM `persistent_roles` WHERE `server`=" + member.getGuild().getId() + " AND `user`=" + member.getId() + " AND `role`=" + role.getId() + ";");
            removePersistentRole.close();
        } catch (SQLException ignored) { }
    }

    @Override
    public List<Role> getPersistentRoles(Member member) {
        List<Role> roles = new ArrayList<>();

        try {
            Statement getPersistentRoles = connection.createStatement();
            ResultSet rs = getPersistentRoles.executeQuery("SELECT `role` FROM `persistent_roles` WHERE `server`=" + member.getGuild().getId() + " AND `user`=" + member.getId() + ";");

            while (rs.next()) {
                roles.add(member.getGuild().getRoleById(rs.getString("role")));
            }

            getPersistentRoles.close();
        } catch (SQLException ignored) { }

        return roles;
    }

    @Override
    public GithubLink getGithubLink(long discordId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT `user`, `github_id`, `github_login` FROM `github_sponsor_links` WHERE `user`=?;")) {
            statement.setLong(1, discordId);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return new GithubLink(rs.getLong("user"), rs.getLong("github_id"), rs.getString("github_login"));
            }
        } catch (SQLException ignored) { }

        return null;
    }

    @Override
    public GithubLink getGithubLinkByGithubId(long githubId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT `user`, `github_id`, `github_login` FROM `github_sponsor_links` WHERE `github_id`=?;")) {
            statement.setLong(1, githubId);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return new GithubLink(rs.getLong("user"), rs.getLong("github_id"), rs.getString("github_login"));
            }
        } catch (SQLException ignored) { }

        return null;
    }

    @Override
    public List<GithubLink> getGithubLinks() {
        List<GithubLink> links = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement("SELECT `user`, `github_id`, `github_login` FROM `github_sponsor_links`;")) {
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                links.add(new GithubLink(rs.getLong("user"), rs.getLong("github_id"), rs.getString("github_login")));
            }
        } catch (SQLException ignored) { }

        return links;
    }

    @Override
    public void setGithubLink(long discordId, long githubId, String githubLogin) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO `github_sponsor_links` (`user`, `github_id`, `github_login`) VALUES (?, ?, ?);")) {
            statement.setLong(1, discordId);
            statement.setLong(2, githubId);
            statement.setString(3, githubLogin);
            statement.executeUpdate();
        } catch (SQLException ignored) { }
    }

    @Override
    public boolean removeGithubLink(long discordId) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM `github_sponsor_links` WHERE `user`=?;")) {
            statement.setLong(1, discordId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ignored) { }

        return false;
    }

    @Override
    public Map<Long, String> getLegacyGithubLinks() {
        Map<Long, String> links = new HashMap<>();

        // The table won't exist if it has already been migrated
        try (PreparedStatement statement = connection.prepareStatement("SELECT `user`, `github` FROM `github_links`;")) {
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                links.put(rs.getLong("user"), rs.getString("github"));
            }
        } catch (SQLException ignored) { }

        return links;
    }

    @Override
    public void dropLegacyGithubLinks() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS `github_links`;");
        } catch (SQLException ignored) { }
    }
}
