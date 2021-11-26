package org.littlestar.jbaidunetdisk;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import com.google.gson.JsonObject;

public class Application {
	// 初始化SLF4J SimpleLogger日志配置.
	static {
		// trace | debug | info | warn | error
		System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "error"); 
		System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-mm-dd HH:mm:ss.SSS");
		System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");
		System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "true");
		System.setProperty(SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true");
		System.setProperty(SimpleLogger.LEVEL_IN_BRACKETS_KEY, "true");
	}
	private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
	
	public static void main(String[] args) throws Exception {
		//// 初始化
		final Terminal terminal = TerminalBuilder.builder()
				//.system(false)
				//.dumb(true)
				.encoding("UTF-8")
				.jna(false)
				.jansi(true)
				.build();
		final LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();

		//lineReader.printAbove("初始化百度云盘小程序, 请稍后...");
		String appName = "";
		String clientId = "";
		String secretKey = "";
		try {
			Properties appInfo = BaiduNetDiskHelper.initAppInfo(lineReader);
			appName = appInfo.getProperty(BaiduNetDiskHelper.APP_NAME_KEY);
			clientId = appInfo.getProperty(BaiduNetDiskHelper.APP_KEY_KEY);
			secretKey = appInfo.getProperty(BaiduNetDiskHelper.SECRET_KEY_KEY);
		} catch (Exception e) {
			LOGGER.error("初始化百度云盘小程序!", e);
			System.exit(-1);
		}
		LOGGER.debug("app_name     : " + appName);
		LOGGER.debug("app_key      : " + clientId);
		LOGGER.debug("secret_key   : " + clientId);

		String accessToken = "";
		String refreshToken = "";
		try {
			Properties authToken = BaiduNetDiskHelper.initAuthorizeToken(lineReader, clientId, secretKey);
			accessToken = authToken.getProperty(BaiduNetDiskHelper.ACCESS_TOKEN_KEY);
			refreshToken = authToken.getProperty(BaiduNetDiskHelper.REFRESH_TOKEN_KEY);
		} catch (Exception e) {
			LOGGER.error("初始化百度云盘访问授权!", e);
			System.exit(-2);
		}
		LOGGER.debug("access_token : " + accessToken);
		LOGGER.debug("refresh_token: " + refreshToken);

		File downloadDir = null;
		try {
			downloadDir = BaiduNetDiskHelper.initDownloadDir();
		} catch (Exception e) {
			LOGGER.error("初始化本地下载目录失败!", e);
		}
		LOGGER.debug("download_dir : " + downloadDir.getAbsolutePath());
		
		//// 如果用户指定了参数, 执行用户指定的命令; 如果没有指定参数, 进入用户交互模式.
		if (args.length > 0) {
			commandLineMode(appName, clientId, secretKey, refreshToken, accessToken, lineReader, args);
		} else {
			//// 进入命令行交互，等待用户命令输入
			stdOutput("没有指定命令参数, 将进入交互模式... 更多帮助通过'help'命令或者指定-h参数获取.");
			try {
				BaiduNetDiskApiHelper.refreshAccessToken(clientId, secretKey, refreshToken);
			} catch (Exception e) {
				LOGGER.error("刷新访问授权令牌失败!", e);
			}
			interactiveMode(appName, accessToken, lineReader);
		}
	}
	
	private static void interactiveMode(String appName, String accessToken, final LineReader lineReader) {
		String baiduName = "";
		try {
			JsonObject userInfo = BaiduNetDiskApiHelper.getUserInfo(accessToken);
			baiduName = userInfo.get("baidu_name").getAsString();
		} catch (Exception e) {
			LOGGER.error("获取用户信息失败!", e);
		}
		LOGGER.debug("baidu_name   : " + baiduName);
		
		String currentDir = "/";
		String promptFormat = "[%s: %s]: ";
		while (true) {
			String input = "";
			try {
				input = lineReader.readLine(String.format(promptFormat, baiduName, currentDir)).trim();
				if (!BaiduNetDiskHelper.isEmpty(input)) {
					String[] commandArray = input.split("\\s+");
					String command = commandArray[0];
					if (Objects.equals(command, "exit")) {
						System.exit(0);
					} else if (Objects.equals(command, "help")) {
						String help = getInteractiveModeHelp();
						lineReader.printAbove(help);
					} else if (Objects.equals(command, "ls")) {
						String targetFile = input.replaceFirst("ls", "").trim();
						BaiduNetDiskHelper.listDir(accessToken, targetFile, currentDir);
					} else if (Objects.equals(command, "delete")) {
						String targetFile = input.replaceFirst("delete", "").trim();
						BaiduNetDiskHelper.delete(accessToken, targetFile, currentDir);
					} else if (Objects.equals(command, "copy")) {
						String options = input.replaceFirst("copy", "").trim();
						String[] optionArray = options.split("\\s+");
						if(optionArray.length != 2) {
							stdOutput("错误的命令格式, 仅简单通过空格区分参数, 所以暂不支持带空格的文件名或者路径, 可以考虑使用命令行模式。");
						} else {
							String source = optionArray[0];
							String dest = optionArray[1];
							BaiduNetDiskHelper.copy(accessToken, source, dest, currentDir);
						}
					} else if (Objects.equals(command, "move")) {
						String options = input.replaceFirst("move", "").trim();
						String[] optionArray = options.split("\\s+");
						if(optionArray.length != 2) {
							stdOutput("错误的命令格式, 仅简单通过空格区分参数, 所以暂不支持带空格的文件名或者路径, 可以考虑使用命令行模式。");
						} else {
							String source = optionArray[0];
							String dest = optionArray[1];
							BaiduNetDiskHelper.move(accessToken, source, dest, currentDir);
						}
					} else if (Objects.equals(command, "rename")) {
						String options = input.replaceFirst("rename", "").trim();
						String[] optionArray = options.split("\\s+");
						if(optionArray.length != 2) {
							stdOutput("错误的命令格式, 仅简单通过空格区分参数, 所以暂不支持带空格的文件名或者路径, 可以考虑使用命令行模式。");
						} else {
							String source = optionArray[0];
							String newName = optionArray[1];
							BaiduNetDiskHelper.rename(accessToken, source, newName, currentDir);
						}
					} else if (Objects.equals(command, "cd")) {
						String targetFile = input.replaceFirst("cd", "").trim();
						currentDir = BaiduNetDiskHelper.changeDir(accessToken, targetFile, currentDir);
					} else if (Objects.equals(command, "download")) {
						String targetFile = input.replaceFirst("download", "").trim();
						BaiduNetDiskHelper.download(accessToken, targetFile, currentDir);
					} else if (Objects.equals(command, "upload")) {
						String targetFile = input.replaceFirst("upload", "").trim();
						try {
							BaiduNetDiskHelper.upload(appName, accessToken, targetFile, "3");
						} catch(Exception e) {
							LOGGER.error("上传失败, 上传文件有许多限制条件, 请参考帮助文档。", e);
							lineReader.printAbove("");
						}
					} else if(Objects.equals(command, "print")) {
						if (commandArray.length > 1) {
							String subCommand = commandArray[1];
							if (Objects.equals(subCommand, "download")) {
								List<DownloadThread> list = DownloadCoordinator.coordinator().getDownloadQueue();
								for (DownloadThread download : list) {
									lineReader.printAbove(download.getFormattedOutput());
								}
							} else if (Objects.equals(subCommand, "upload")) {
								List<UploadThread> list = UploadCoordinator.coordinator().getUploadQueue();
								for (UploadThread upload : list) {
									stdOutput(upload.getFormattedOutput());
								}
							} else if (Objects.equals(subCommand, "quota")) {
								JsonObject quota = BaiduNetDiskApiHelper.getDiskQuota(accessToken);
								long total = quota.get("total").getAsLong();
								long used = quota.get("used").getAsLong();
								stdOutput("网盘总容量: " + BaiduNetDiskHelper.bytesToPretty(total, 2));
								stdOutput("已使用容量: " + BaiduNetDiskHelper.bytesToPretty(used, 2));
							} else if (Objects.equals(subCommand, "userinfo")) {
								JsonObject userInfo = BaiduNetDiskApiHelper.getUserInfo(accessToken); 
								String bName = userInfo.get("baidu_name").getAsString(); // 百度账号
								String netdiskName = userInfo.get("netdisk_name").getAsString();
								int vipType = userInfo.get("vip_type").getAsInt();
								String uk = userInfo.get("uk").getAsString();
								String strVipType = "普通用户";
								if (vipType == 1) {
									strVipType = "普通会员";
								} else if (vipType == 2) {
									strVipType = "超级会员";
								}
								stdOutput("百度账号: " + bName);
								stdOutput("网盘账号: " + netdiskName);
								stdOutput("会员类型: " + strVipType);
								stdOutput("用户ID : " + uk);
							}
						}
					} else {
						stdOutput("不支持的命令: " + command + ".");
					}
				}
			} catch (UserInterruptException e) {
				LOGGER.error("用户终止...", e);
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		}
	}
	
	private static String getInteractiveModeHelp() {
		String help = 
				"支持的命令列表: \n" +
				"  help     : 显示帮助。\n" +
				"  cd <Path>: 进入网盘指定的目录。\n"+
				"  ls [Path]: 列出指定的网盘目录下的文件列表，如果不指定显示当前网盘目录下的文件列表。\n"+
				"  download <Path> : 下载指定网盘路径下的文件/目录。\n" +
				"  upload <Path>   : 上传指定本地路径下的文件/目录到网盘，目前不支持带中文的路径。\n" +
				"  delete <Path>   : 删除指定的目录/文件(放入网盘回收站)。\n"+
				"  copy <source> <dest>      : 拷贝source目录/文件到dest位置。\n" +
				"  move <source> <dest>      : 移动source目录/文件到dest位置。\n" +
				"  rename <source> <newname> : 将source重命名为newname。\n" +
				"  print download  : 打印当前的下载列表。\n" +
				"  print upload    : 打印当前的上传列表。\n" +
				"  print quota     : 显示网盘的容量信息。\n" +
				"  print userinfo  : 显示当前网盘用户信息。\n";
		return help;
	}
	
	private static void commandLineMode(String appName, String clientId, String secretKey, String refreshToken,
			String accessToken, final LineReader lineReader, String[] args) throws Exception {
		final CommandLineParser cliParser = new DefaultParser();
		final Options options = CmdHelper.setupOptions();
		final CommandLine cli = cliParser.parse(options, args);
		boolean isVerbose = false;
		if (cli.hasOption(CmdHelper.VERBOSE_OPTION)) {
			isVerbose = true;
		}
		if (cli.hasOption(CmdHelper.HELP_OPTION)) {
			CmdHelper.showCmdHelp(options);
		} else if (cli.hasOption(CmdHelper.REFRESH_OPTION)) {
			try {
				BaiduNetDiskApiHelper.refreshAccessToken(clientId, secretKey, refreshToken);
			} catch (Exception e) {
				LOGGER.error("刷新访问授权令牌失败!", e);
			}
		} else if (cli.hasOption(CmdHelper.LIST_OPTION)) {
			String targetFile = cli.getOptionValue(CmdHelper.LIST_OPTION).trim();
			BaiduNetDiskHelper.listDir(accessToken, targetFile, "/");
		} else if (cli.hasOption(CmdHelper.DOWNLOAD_OPTION)) {
			String targetFile = cli.getOptionValue(CmdHelper.DOWNLOAD_OPTION).trim();
			BaiduNetDiskHelper.download(accessToken, targetFile, "/");
			stdOutput("开始下载'" + targetFile + "'...", true);
			DownloadCoordinator coordinator = DownloadCoordinator.coordinator();
			int sleepCount = 0;
			while (coordinator.getPendingTaskCount() > 0L) {
				// 指定verbose, 每4秒输出一次进度.
				if (isVerbose && sleepCount % 8 == 0) {
					stdOutput("");
					stdOutput(coordinator.getCompletedTaskCount() + " of " + coordinator.getTaskCount()
							+ " download done.", true);
					stdOutput(DownloadThread.getFormattedOutputHeader());
					List<DownloadThread> list = coordinator.getDownloadQueue();
					for (DownloadThread download : list) {
						// if (download.getState().equals(State.DOWNLOADING)) {
						stdOutput(download.getFormattedOutput());
						// }
					}
				}
				Thread.sleep(500L);
				sleepCount++;
			}
			stdOutput("下载完成.", true);
		} else if (cli.hasOption(CmdHelper.UPLOAD_OPTION)) {
			String targetFile = cli.getOptionValue(CmdHelper.UPLOAD_OPTION).trim();
			BaiduNetDiskHelper.upload(appName, accessToken, targetFile, "3");
			stdOutput("开始上传'" + targetFile + "'...", true);
			UploadCoordinator coordinator = UploadCoordinator.coordinator();
			int sleepCount = 0;
			while (coordinator.getPendingTaskCount() > 0L) {
				if (isVerbose && sleepCount % 8 == 0) {
					stdOutput("");
					stdOutput(coordinator.getCompletedTaskCount() + " of " + coordinator.getTaskCount() + " upload done.", true);
					stdOutput(DownloadThread.getFormattedOutputHeader());
					List<UploadThread> list = coordinator.getUploadQueue();
					for (UploadThread upload : list) {
						stdOutput(upload.getFormattedOutput());
					}
				}
				Thread.sleep(500L);
				sleepCount++;
			}
			stdOutput("上传完成.", true);
		} else if (cli.hasOption(CmdHelper.MOVE_OPTION)) {
			String[] opts = cli.getOptionValues(CmdHelper.MOVE_OPTION);
			if(opts.length == 2) {
				String source = opts[0];
				String dest = opts[1];
				JsonObject response = BaiduNetDiskHelper.move(accessToken, source, dest, "/");
				if (isVerbose) {
					stdOutput(response.toString());
				}
			} else {
				stdOutput("错误的命令格式, 需要指定原路劲和目的路径。如果路径包含空格, 请使用双引号括起来, 如-m \"/my dir/\" my_dir ");
			}
		} else if (cli.hasOption(CmdHelper.COPY_OPTION)) {
			String[] opts = cli.getOptionValues(CmdHelper.COPY_OPTION);
			if(opts.length == 2) {
				String source = opts[0];
				String dest = opts[1];
				JsonObject response = BaiduNetDiskHelper.copy(accessToken, source, dest, "/");
				if (isVerbose) {
					stdOutput(response.toString());
				}
			} else {
				stdOutput("错误的命令格式, 需要指定原路劲和目的路径。如果路径包含空格, 请使用双引号括起来, 如-m \"/my dir/\" my_dir ");
			}
		} else if (cli.hasOption(CmdHelper.DELETE_OPTION)) {
			String targetFile = cli.getOptionValue(CmdHelper.DELETE_OPTION).trim();
			JsonObject response = BaiduNetDiskHelper.delete(accessToken, targetFile, "/");
			if (isVerbose) {
				stdOutput(response.toString());
			}
		} else if (cli.hasOption(CmdHelper.RENAME_OPTION)) {
			String[] opts = cli.getOptionValues(CmdHelper.RENAME_OPTION);
			if(opts.length == 2) {
				String source = opts[0];
				String newName = opts[1];
				JsonObject response = BaiduNetDiskHelper.rename(accessToken, source, newName, "/");
				if (isVerbose) {
					stdOutput(response.toString());
				}
			} else {
				stdOutput("错误的命令格式, 需要指定原路劲和目的路径。如果路径包含空格, 请使用双引号括起来, 如-m \"/my dir/\" my_dir ");
			}
		}
		System.exit(0);
	}
	
	public static String getCurrentTimestamp() {
        final LocalDateTime dateTime = LocalDateTime.now();
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(dateTime);
        return timestamp;
	}
	
	public static void stdOutput(String msg, boolean withTimestamp) {
		if (withTimestamp) {
			msg = "[" + getCurrentTimestamp() + "]: " + msg;
		}
		System.out.println(msg);
	}

	public static void stdOutput(String msg) {
		stdOutput(msg, false);
	}
}
