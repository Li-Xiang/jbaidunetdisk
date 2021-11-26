package org.littlestar.jbaidunetdisk;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;

public class CmdHelper {
	public static final String HELP_OPTION         = "help";
	public static final String DOWNLOAD_OPTION     = "download";
	public static final String UPLOAD_OPTION       = "upload";
	public static final String LIST_OPTION         = "list";
	public static final String MOVE_OPTION         = "move";
	public static final String COPY_OPTION         = "copy";
	public static final String DELETE_OPTION       = "delete";
	public static final String RENAME_OPTION       = "rename";
	public static final String REFRESH_OPTION      = "refresh";
	public static final String VERBOSE_OPTION      = "verbose";
	public static Options setupOptions() {
		OptionGroup optionGroup = new OptionGroup();
		optionGroup.addOption(Option.builder("h").longOpt(HELP_OPTION).desc("打印帮助。").build());
		optionGroup.addOption(Option.builder("d").longOpt(DOWNLOAD_OPTION).hasArg().required(true)
				.argName("path").desc("下载指定路径下的网盘文件或者目录, 需要指定绝对路径。").build());
		optionGroup.addOption(Option.builder("u").longOpt(UPLOAD_OPTION).hasArg().required(true)
				.argName("path").desc("上传指定路径下的文件或者目录到网盘。").build());
		optionGroup.addOption(Option.builder("l").longOpt(LIST_OPTION).hasArg().required(true)
				.argName("path").desc("列出指定路径下的网盘文件或者目录，需要指定绝对路径。").build());
		optionGroup.addOption(Option.builder("x").longOpt(DELETE_OPTION).hasArg().required(true)
				.argName("path").desc("删除指定<path>的目录/文件，需要指定绝对路径。").build());
		optionGroup.addOption(Option.builder("m").longOpt(MOVE_OPTION).hasArgs().required(true)
				.argName("soure> <dest").desc("移动<source>目录/文件到<dest>，需要指定绝对路径。").build());
		optionGroup.addOption(Option.builder("c").longOpt(COPY_OPTION).hasArgs().required(true)
				.argName("soure> <dest").desc("拷贝<source>目录/文件到<dest>，需要指定绝对路径。").build());
		optionGroup.addOption(Option.builder("n").longOpt(RENAME_OPTION).hasArgs().required(true)
				.argName("soure> <newname").desc("将<source>重命名为<newName>，需要指定绝对路径。").build());
		optionGroup.addOption(Option.builder("r").longOpt(REFRESH_OPTION).desc("刷新Access Token。").build());
		Option verboseOption = Option.builder("v").longOpt(VERBOSE_OPTION).desc("显示详细输出。").build();
	    Options options = new Options();
	    options.addOptionGroup(optionGroup);
	    options.addOption(verboseOption);
	    return options;
	}
	
	public static void showCmdHelp(Options options) {
		String cmdLineSyntax = "jbaidunetdisk [option]\n"
				+"注 意: \n"
				+ "  1. 如果不指定参数将进入交互模式; \n"
				+ "  2. 除了-v,--verbose选项外, 其他选项(option)都是互斥的, 同时只能指定一个; \n"
				+ "  3. 命令行模式仅支持绝对路径, 路径/文件名严格区分大小写; \n"
				+ "  4. 如果路径/文件名如果包含空格, 需要使用双引号包含起来;\n";
		String header = "\noption: \n";
		String footer = "\n使用案例:\n"
				+"列出'/apps dir/'目录下的所有文件\n"
				+"$ jbaidunetdisk -l \"/apps dir\"\n\n"
				+"下载'/apps dir/'目录下所有文件\n"
				+"$ jbaidunetdisk -d \"/apps dir\"\n\n";
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(120);
        formatter.printHelp(cmdLineSyntax, header, options, footer);
    }

}
