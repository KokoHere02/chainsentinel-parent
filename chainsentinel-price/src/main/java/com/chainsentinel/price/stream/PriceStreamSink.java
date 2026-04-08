package com.chainsentinel.price.stream;

public interface PriceStreamSink {

	void onQuote(PriceStreamQuote quote);
}