package com.chainsentinel.price.stream;

public interface PriceTickBatchWriter {

	void enqueue(PriceStreamQuote quote);

}

