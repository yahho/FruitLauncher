package net.teamfruit.skcraft.launcher.launch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Level;

import javax.swing.JOptionPane;

import org.apache.commons.lang.StringUtils;

import com.beust.jcommander.internal.Maps;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.LauncherUtils;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.MessageLog;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.java.Log;
import net.teamfruit.skcraft.launcher.discordrpc.DiscordStatus;
import net.teamfruit.skcraft.launcher.discordrpc.LauncherStatus;
import net.teamfruit.skcraft.launcher.discordrpc.LauncherStatus.NullDisablable;
import net.teamfruit.skcraft.launcher.model.modpack.ConnectServerInfo;

@Log
public class ExitHandler {
	private final int exitcode;
	private String[] logLines;
	private final Instance instance;
	private final Session session;
	private final ConnectServerInfo server;

	public ExitHandler(int exitcode, MessageLog log, final Instance instance, final Session session, final ConnectServerInfo server) {
		this.exitcode = exitcode;
		if (exitcode!=0) {
			String logString = log.getMessage().getPastableText();
			this.logLines = logString.split("\n");
		}
		this.instance = instance;
		this.session = session;
		this.server = server;
	}

	private final @Getter(lazy = true, value = AccessLevel.PRIVATE) String restart = createRestart();

	private String createRestart() {
		if (exitcode==-2&&logLines!=null) {
			String restartText = null;

			for (int file = logLines.length-1; file>=0; --file) {
				String inputStream = logLines[file];
				int e = inputStream.lastIndexOf("#!@!#");

				if (e>=0&&e<inputStream.length()-"#!@!#".length()-1) {
					restartText = inputStream.substring(e+"#!@!#".length()).trim();
					break;
				}
			}

			return restartText;
		}
		return null;
	}

	public boolean handleRestart() {
		return getRestart()!=null;
	}

	public ConnectServerInfo getRestartServer() {
		String data = getRestart();
		try {
			return Persistence.getMapper().readValue(data, ConnectServerInfo.class);
		} catch (Exception e) {
		}
		return null;
	}

	private final @Getter(lazy = true, value = AccessLevel.PRIVATE) boolean crashReport = createCrashReport();

	private boolean createCrashReport() {
		if (logLines!=null) {
			String errorText = null;

			for (int file = logLines.length-1; file>=0; --file) {
				String inputStream = logLines[file];
				int e = inputStream.lastIndexOf("#@!@#");

				if (e>=0&&e<inputStream.length()-"#@!@#".length()-1) {
					errorText = inputStream.substring(e+"#@!@#".length()).trim();
					break;
				}
			}

			if (errorText!=null) {
				this.errorFile = new File(errorText);

				if (errorFile.isFile()) {
					log.info("Crash report detected, opening: "+errorText);
					FileInputStream input = null;

					try {
						input = new FileInputStream(errorFile);
						BufferedReader reader = new BufferedReader(new InputStreamReader(input));
						StringBuilder result;
						String line;
						for (result = new StringBuilder(); (line = reader.readLine())!=null; result.append(line))
							if (result.length()>0)
								result.append("\n");

						reader.close();

						this.errorText = result.toString();
					} catch (IOException arg13) {
						log.log(Level.WARNING, "Couldn\'t open crash report", arg13);
					} finally {
						LauncherUtils.closeQuietly(input);
					}
				} else
					log.log(Level.WARNING, "Crash report detected, but unknown format: "+errorText);

				return true;
			}
		}
		return false;
	}

	public boolean handleCrashReport() {
		return isCrashReport();
	}

	private File errorFile;
	private String errorText;

	public File getErrorFile() {
		if (isCrashReport()) {
			try {
				return errorFile.getCanonicalFile();
			} catch (IOException e) {
			}
			return errorFile;
		}
		return null;
	}

	public String getErrorText() {
		if (isCrashReport())
			return errorText;
		return null;
	}

	public void showCrashReport() {
		if (isCrashReport()) {
			File errorFile = getErrorFile();
			String errorText = getErrorText();
			if (errorFile!=null&&errorText!=null) {
				Map<String, String> status = Maps.newHashMap();
				status.put("instance", instance.getTitle());
				if (StringUtils.isEmpty(instance.getKey())) {
					ConnectServerInfo server = instance.getServer();
					if (server!=null)
						status.put("server", server.toString());
				}
				status.put("player", session.getName());
				LauncherStatus.instance.open(DiscordStatus.CRASHED, new NullDisablable(), status);
				SwingHelper.showMessageDialog(null, SharedLocale.tr("runner.crashMinecraft", errorFile.getName()),
						SharedLocale.tr("runner.crashMinecraftTitle"), errorText.toString(), JOptionPane.ERROR_MESSAGE);
				LauncherStatus.instance.close(DiscordStatus.CRASHED);
			}
		}
	}
}
