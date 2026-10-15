/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

/**
 * ContentType
 */
public enum ContentType {

	HTML("text/html; charset=utf-8"),
	JSON("application/json; charset=utf-8"),
	EVENT_STREAM("text/event-stream; charset=utf-8"),
	XML("text/xml; charset=utf-8"),
	TEXT("text/plain; charset=utf-8"),
	JAVASCRIPT("application/javascript; charset=utf-8");

	public final String value;

	ContentType(String value) {
		this.value = value;
	}

	public String toString() {
		return value;
	}
}


