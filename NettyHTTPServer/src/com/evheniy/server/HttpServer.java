package com.evheniy.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class HttpServer {
	private int port;
	protected static List<String> stat = new Vector<String>();
	protected static Map<String, Integer> redirects = new HashMap<String, Integer>();
	protected static int connectionsActive = 0;

	public HttpServer(int port) {
		this.port = port;
	}

	public void run() throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childHandler(new HttpServerInitializer());
			System.out.println("Server started! Listening at port:" + port);

			// Bind and start to accept incoming connections.
			ChannelFuture f = b.bind(port).sync();
			f.channel().closeFuture().sync();

		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}

	public static void main(String[] args) throws Exception {
		int port;
		Date now = new Date();
		String date = DateFormat.getDateTimeInstance(DateFormat.SHORT,
				DateFormat.MEDIUM).format(now);
		System.out.println(date);
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		} else {
			port = 8080;
		}
		new HttpServer(port).run();
	}
}