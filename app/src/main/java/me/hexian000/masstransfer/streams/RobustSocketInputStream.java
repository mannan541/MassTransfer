package me.hexian000.masstransfer.streams;

import java.io.IOException;
import java.io.InputStream;

/*
 * RobustSocketInputStream 是对系统自带TCP Socket流的封装
 * TODO 实现数据传输、窗缓存和ACK
 */

public class RobustSocketInputStream extends InputStream {
	private RobustSocket socket;

	RobustSocketInputStream(RobustSocket socket) {
		this.socket = socket;
	}

	@Override
	public int read() throws IOException {
		while (true) {
			try {
				return socket.in.read();
			} catch (IOException e) {
				socket.reconnect();
			}
		}
	}
}
