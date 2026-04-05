package com.chainsentinel.price.provider.okx.dto;

import java.util.List;

public class OkxTickerResponse {

	private String code;
	private String msg;
	private List<OkxTickerData> data;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public List<OkxTickerData> getData() {
		return data;
	}

	public void setData(List<OkxTickerData> data) {
		this.data = data;
	}

	public static class OkxTickerData {
		private String instId;
		private String last;
		private String ts;

		public String getInstId() {
			return instId;
		}

		public void setInstId(String instId) {
			this.instId = instId;
		}

		public String getLast() {
			return last;
		}

		public void setLast(String last) {
			this.last = last;
		}

		public String getTs() {
			return ts;
		}

		public void setTs(String ts) {
			this.ts = ts;
		}
	}
}
