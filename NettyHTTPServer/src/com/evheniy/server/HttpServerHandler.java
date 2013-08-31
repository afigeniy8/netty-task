package com.evheniy.server;

import static io.netty.handler.codec.http.HttpHeaders.Names.LOCATION;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.CharsetUtil;

import java.net.SocketAddress;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

//let the magic begin
public class HttpServerHandler extends SimpleChannelInboundHandler<Object> {

	private String date;
	private Date now = new Date();

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg)
			throws Exception {

		int bytesSent = handleHttpRequest(ctx, (FullHttpRequest) msg);
		FullHttpRequest req = (FullHttpRequest) msg;
		String uri = req.getUri();

		String amountRecieved = msg.toString();
		byte[] amountRecievedBytes = amountRecieved.getBytes();

		SocketAddress remoteAddress = ctx.channel().remoteAddress();
		System.out.println("Connection established at " + remoteAddress
				+ " uri: " + uri);
		this.date = DateFormat.getDateTimeInstance(DateFormat.SHORT,
				DateFormat.MEDIUM).format(now);
		String correctIP = remoteAddress.toString();
		correctIP = correctIP.substring(0, correctIP.lastIndexOf(":"));
		HttpServer.stat.add(correctIP + ";" + uri + ";" + date + ";"
				+ bytesSent + ";" + amountRecievedBytes.length);

	}

	// handle incoming http request and send response
	public int handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req)
			throws Exception {
		int bytesSent = 0;
		String[] tmp = null;
		FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK);

		if (req.getUri().contains("redirect")) {

			if (!HttpServer.redirects.containsKey(req.getUri())) {
				HttpServer.redirects.put(req.getUri(), 1);
			} else {
				HttpServer.redirects.put(req.getUri(),
						HttpServer.redirects.get(req.getUri()) + 1);
			}
			tmp = req.getUri().split("=");
			req.setUri(tmp[1]);
			sendRedirect(ctx, tmp[1]);

		} else if (req.getUri().endsWith("hello")) {

			ByteBuf buf = Unpooled
					.copiedBuffer("Helloworld", CharsetUtil.UTF_8);

			res = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
			Thread.sleep(10000);
			sendHttpResponse(ctx, res);

		} else if (req.getUri().endsWith("status")) {
			ByteBuf buf = Unpooled.copiedBuffer(getStatus(), CharsetUtil.UTF_8);

			res = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
			sendHttpResponse(ctx, res);

		} else if ("/favicon.ico".equals(req.getUri())) {
			res = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
			sendHttpResponse(ctx, res);
		} else {
			sendHttpResponse(ctx, res);
		}

		String amount = res.toString();
		bytesSent = amount.getBytes().length;
		return bytesSent;
	}

	// returns the html page of statistics
	private String getStatus() {
		String htmlBegin = "<html><head></head>";
		String htmlEnd = "</html>";
		String result = "";
		String newLine = "<br></br>";

		result += htmlBegin;

		result += "Общее количество запросов: " + HttpServer.stat.size()
				+ newLine;
		result += "Количество уникальных запросов (по одному на IP): "
				+ getUnique() + newLine;
		result += "Счетчик запросов на каждый IP: " + newLine
				+ getConnectionCounter() + newLine;
		result += "Количество переадресаций по url: " + newLine
				+ getRedirects() + newLine;
		result += "Количество открытых соединений : "
				+ HttpServer.connectionsActive + newLine;
		result += "Таблица логов последних 16ти соединений: " + newLine
				+ getLogTable();

		result += htmlEnd;
		return result;
	}

	// returns the amount of unique requests (uri's)
	private int getUnique() {
		String tmp[] = null;
		String uri = "";
		Set<String> unique = new HashSet<String>();

		for (Iterator<String> iterator = HttpServer.stat.iterator(); iterator
				.hasNext();) {
			String curr = (String) iterator.next();
			tmp = curr.split(";");
			uri = tmp[1];

			if (!unique.contains(uri)) {
				unique.add(uri);
			}

		}

		return unique.size();
	}

	// returns the amount of requests per IP
	private String getConnectionCounter() {
		Map<String, Integer> requests = new HashMap<String, Integer>();
		String ip = "";
		String toReturn = "<table border = \"1\" >";
		String[] tmp = null;

		for (Iterator<String> iterator = HttpServer.stat.iterator(); iterator
				.hasNext();) {
			String curr = (String) iterator.next();

			tmp = curr.split(";");
			ip = tmp[0];

			if (!requests.containsKey(ip)) {
				requests.put(ip, 1);
			} else {
				requests.put(ip, requests.get(ip) + 1);
			}
		}

		Iterator<Entry<String, Integer>> it = requests.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry) it.next();
			toReturn += "<tr>" + "<td> IP: " + pairs.getKey()
					+ "</td> <td> запросов: " + pairs.getValue()
					+ " </td> </tr>";
		}
		toReturn += "</table>";
		return toReturn;
	}

	// returns the IP and amount of redirects per IP
	private String getRedirects() {
		String toReturn = "<table border = \"1\" >";
		if (HttpServer.redirects.size() == 0) {
			toReturn += "0";
		} else {
			Iterator<Entry<String, Integer>> it = HttpServer.redirects
					.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry pairs = (Map.Entry) it.next();
				toReturn += "<tr>" + "<td> url: " + pairs.getKey()
						+ "</td> <td> переадресаций: " + pairs.getValue()
						+ " </td> </tr>";
			}
		}
		toReturn += "</table>";
		return toReturn;
	}

	// returns last 16 log entries
	private String getLogTable() {
		String toReturn = "<table border=\"1\">";
		String[] tmp = null;
		final int DISPLAY_AMOUNT = 16;
		int i = 1;

		if (HttpServer.stat.size() <= DISPLAY_AMOUNT) {
			for (Iterator<String> iterator = HttpServer.stat.iterator(); iterator
					.hasNext();) {
				String curr = (String) iterator.next();
				tmp = curr.split(";");

				toReturn += "<tr> <td> #" + (i + 1) + " </td> <td> IP:"
						+ tmp[0] + "</td> <td> URI:" + tmp[1]
						+ "</td> <td> date:" + tmp[2]
						+ "</td> <td> bytes_sent " + tmp[3]
						+ "</td> <td> recieved_bytes " + tmp[4]
						+ " </td> </tr>";
				i++;
			}
		} else {
			for (i = HttpServer.stat.size() - 1; i > HttpServer.stat.size() - 17; i--) {
				String curr = HttpServer.stat.get(i);
				tmp = curr.split(";");

				toReturn += "<tr> <td> #" + (i + 1) + " </td>  <td> IP:"
						+ tmp[0] + "</td> <td> URI:" + tmp[1]
						+ "</td> <td> date:" + tmp[2]
						+ "</td> <td> bytes_sent " + tmp[3]
						+ "</td> <td> recieved_bytes " + tmp[4]
						+ " </td> </tr>";
			}
		}

		toReturn += "</table>";
		return toReturn;
	}

	// sends redirect Response to new uri
	public void sendRedirect(ChannelHandlerContext ctx, String newUri) {
		FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
		res.headers().set(LOCATION, "http://" + newUri);
		ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);

	}

	// sends usual http response
	public void sendHttpResponse(ChannelHandlerContext ctx, FullHttpResponse res)
			throws Exception {
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		f.addListener(ChannelFutureListener.CLOSE);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
		if (ctx.channel().isActive()) {
			HttpServer.connectionsActive++;
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		cause.printStackTrace();
		ctx.close();
	}

}
