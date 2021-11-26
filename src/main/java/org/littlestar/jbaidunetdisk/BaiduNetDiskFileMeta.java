package org.littlestar.jbaidunetdisk;

public class BaiduNetDiskFileMeta {
	private long category;
	private String dlink;
	private String filename;
	private long fs_id;
	private int isdir;
	private String md5;
	private long oper_id;
	private String path;
	private long server_ctime;
	private long server_mtime;
	private long size;

	public long getCategory() {
		return category;
	}

	public void setCategory(long category) {
		this.category = category;
	}

	public String getDlink() {
		return dlink;
	}

	public void setDlink(String dlink) {
		this.dlink = dlink;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public long getFsId() {
		return fs_id;
	}

	public void setFsId(long fs_id) {
		this.fs_id = fs_id;
	}

	public int getIsdir() {
		return isdir;
	}

	public void setIsdir(int isdir) {
		this.isdir = isdir;
	}

	public String getMd5() {
		return md5;
	}

	public void setMd5(String md5) {
		this.md5 = md5;
	}

	public long getOperId() {
		return oper_id;
	}

	public void setOperId(long oper_id) {
		this.oper_id = oper_id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public long getServerCtime() {
		return server_ctime;
	}

	public void setServerCtime(long server_ctime) {
		this.server_ctime = server_ctime;
	}

	public long getServerMtime() {
		return server_mtime;
	}

	public void setServerMtime(long server_mtime) {
		this.server_mtime = server_mtime;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

}
