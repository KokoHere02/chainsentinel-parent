package com.chainsentinel.price.stream.ws;

public interface WebSocketMessageHandler {

	void onOpen();

	void onText(String text);

	void onClose(int statusCode, String reason);

	void onError(Throwable error);
}