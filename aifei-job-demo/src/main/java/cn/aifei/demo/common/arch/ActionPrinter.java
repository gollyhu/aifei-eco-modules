/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import cn.aifei.aop.Interceptor;
import cn.aifei.core.Aifei;
import cn.aifei.router.Action;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ActionPrinter
 */
public class ActionPrinter {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String title = "\nAifei-" + Aifei.getVersion() + " action info (";

    public void print(Action action, In in) {
        StringBuilder sb = new StringBuilder(512);

        // 打印 title、path、action
        sb.append(title).append(LocalDateTime.now().format(formatter)).append(") --------------------------------");
        sb.append("\nPath        : ").append(action.getActionPath());
        sb.append("\nAction      : ").append(action.getBriefInfo());

        // 打印拦截器
        Interceptor[] inters = action.getInterceptors();
        if (inters.length > 0) {
            sb.append("\nInterceptor : ");
            for (int i = 0; i < inters.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(inters[i].getClass().getSimpleName());
            }
        }

        // 打印路径参数
        if (in.pathParaStr != null && !in.pathParaStr.isEmpty()) {
            sb.append("\nPathPara    : ").append(in.pathParaStr);
        }

        // 打印参数
        JSONObject data = in.getData(); // 触发参数解析
        JSONArray arrayData = in.getArrayData();
        sb.append("\nParameter   : ");
        if (arrayData == null) {
            sb.append(data.toJSONString());
        } else {
            sb.append(arrayData.toJSONString());
        }

        System.out.println(sb);
    }
}



