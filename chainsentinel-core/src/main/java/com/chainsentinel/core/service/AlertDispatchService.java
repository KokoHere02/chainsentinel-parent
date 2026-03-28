package com.chainsentinel.core.service;

public interface AlertDispatchService {

    int dispatchPending();

    boolean retryOne(Long alertId);
}
