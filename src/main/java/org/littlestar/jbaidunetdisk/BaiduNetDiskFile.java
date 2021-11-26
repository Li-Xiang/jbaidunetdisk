package org.littlestar.jbaidunetdisk;

import java.text.SimpleDateFormat;
import java.util.Date;

public class BaiduNetDiskFile {
	
	private long tkbind_id;
	private long owner_type;
	private long category;
	private String real_category;
	private long fs_id;
	private long oper_id;
	private long server_ctime;
	private int extent_tinyint7;
	private long wpfile;
	private long local_mtime;
	private long size;
	private long server_mtime;
	private String path;
	private int share;
	private long server_atime;
	private long pl;
	private long local_ctime;
	private String server_filename;
	private String md5;
	private long owner_id;
	private long unlist;
	private int isdir;

	public long getTkbindId() {
		return tkbind_id;
	}

	public void setTkbindId(long tkbind_id) {
		this.tkbind_id = tkbind_id;
	}

	public long getOwnerType() {
		return owner_type;
	}

	public void setOwnerType(long owner_type) {
		this.owner_type = owner_type;
	}

	public long getCategory() {
		return category;
	}

	public void setCategory(long category) {
		this.category = category;
	}

	public String getRealCategory() {
		return real_category;
	}

	public void setRealCategory(String real_category) {
		this.real_category = real_category;
	}

	public long getFsId() {
		return fs_id;
	}

	public void setFsId(long fs_id) {
		this.fs_id = fs_id;
	}

	public long getOperId() {
		return oper_id;
	}

	public void setOperId(long oper_id) {
		this.oper_id = oper_id;
	}

	public Date getServerCtime() {
		return new Date(server_ctime * 1000L );
	}

	public void setServerCtime(long server_ctime) {
		this.server_ctime = server_ctime;
	}

	public int getExtentTinyint7() {
		return extent_tinyint7;
	}

	public void setExtentTinyint7(int extent_tinyint7) {
		this.extent_tinyint7 = extent_tinyint7;
	}

	public long getWpfile() {
		return wpfile;
	}

	public void setWpfile(long wpfile) {
		this.wpfile = wpfile;
	}

	public long getLocalMtime() {
		return local_mtime;
	}

	public void setLocalMtime(long local_mtime) {
		this.local_mtime = local_mtime;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public long getServerMtime() {
		return server_mtime;
	}

	public void setServerMtime(long server_mtime) {
		this.server_mtime = server_mtime;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getShare() {
		return share;
	}

	public void setShare(int share) {
		this.share = share;
	}

	public long getServerAtime() {
		return server_atime;
	}

	public void setServerAtime(long server_atime) {
		this.server_atime = server_atime;
	}

	public long getPl() {
		return pl;
	}

	public void setPl(long pl) {
		this.pl = pl;
	}

	public long getLocalCtime() {
		return local_ctime;
	}

	public void setLocalCtime(long local_ctime) {
		this.local_ctime = local_ctime;
	}

	public String getServerFilename() {
		return server_filename;
	}

	public void setServerFilename(String server_filename) {
		this.server_filename = server_filename;
	}

	public String getMd5() {
		return md5;
	}

	public void setMd5(String md5) {
		this.md5 = md5;
	}

	public long getOwnerId() {
		return owner_id;
	}

	public void setOwnerId(long owner_id) {
		this.owner_id = owner_id;
	}

	public long getUnlist() {
		return unlist;
	}

	public void setUnlist(long unlist) {
		this.unlist = unlist;
	}

	/**
	 * 是否目录，0 文件、1 目录
	 * @return
	 */
	public int getIsdir() {
		return isdir;
	}
	
	public boolean isDir() {
		return (isdir == 0) ? false : true;
	}

	public void setIsdir(int isdir) {
		this.isdir = isdir;
	}
	
	private static final String OUTPUT_FORMAT = "%-17s %-20s %-6s %-9s %s";
	public final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"; 
	public static String getFormattedOutputHeader() {
		return String.format(OUTPUT_FORMAT, "FS_ID", "CREATE TIME", "DIR?", "SIZE", "FILE NAME")
				+ "\n"
				+ String.format(OUTPUT_FORMAT, 
						"-----------------", 
						"--------------------", 
						"------", 
						"---------", 
						"--------------------");
	}

	public String getFormattedOutput() {
		String strFsId = Long.toString(fs_id);
		Date serverCtime = getServerCtime();
		String strSize = (isdir == 0) ? BaiduNetDiskHelper.bytesToPretty(size, 2) : "";
		String dirFlag = (isdir == 0) ? "" : "<DIR>";
		SimpleDateFormat dtFormat = new SimpleDateFormat(DATE_TIME_PATTERN);
		return String.format(OUTPUT_FORMAT, strFsId, dtFormat.format(serverCtime), dirFlag, strSize, server_filename);
	}
}
