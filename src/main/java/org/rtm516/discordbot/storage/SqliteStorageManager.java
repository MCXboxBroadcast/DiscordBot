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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SqliteStorageManager extends MySQLStorageManager {

    @Override
    public void setupStorage() throws Exception {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + PropertiesManager.getDatabase());

        Statement createTables = connection.createStatement();
        createTables.executeUpdate("CREATE TABLE IF NOT EXISTS `preferences` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `server` INTEGER NOT NULL, `key` VARCHAR(32), `value` TEXT NOT NULL, CONSTRAINT `pref_constraint` UNIQUE (`server`,`key`));");
        createTables.executeUpdate("CREATE TABLE IF NOT EXISTS `persistent_roles` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `server` INTEGER NOT NULL, `user` INTEGER NOT NULL, `role` INTEGER NOT NULL, CONSTRAINT `role_constraint` UNIQUE (`server`,`user`,`role`));");
        createTables.executeUpdate("CREATE TABLE IF NOT EXISTS `github_links` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `user` INTEGER NOT NULL, `github` VARCHAR(32), CONSTRAINT `github_constraint` UNIQUE (`user`,`github`));");
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
    public String getGithubUsername(long user) {
        try {
            Statement getGithubLink = connection.createStatement();
            ResultSet rs = getGithubLink.executeQuery("SELECT `github` FROM `github_links` WHERE `user`=" + user + ";");

            if (rs.next()) {
                return rs.getString("github");
            }

            getGithubLink.close();
        } catch (SQLException ignored) { }

        return null;
    }

    @Override
    public long getDiscordId(String username) {
        try {
            Statement getGithubLink = connection.createStatement();
            ResultSet rs = getGithubLink.executeQuery("SELECT `user` FROM `github_links` WHERE `github`='" + username + "';");

            if (rs.next()) {
                return rs.getLong("user");
            }

            getGithubLink.close();
        } catch (SQLException ignored) { }

        return 0L;
    }

    @Override
    public void setGithubUsername(long user, String username) {
        try {
            Statement addGithubLink = connection.createStatement();
            addGithubLink.executeUpdate("INSERT OR REPLACE INTO `github_links` (`user`, `github`) VALUES (" + user + ", '" + username + "');");
            addGithubLink.close();
        } catch (SQLException ignored) { }
    }
}
