package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.json.JSONObject;
import cn.zhangheng.common.httpServer.auth.AuthManager;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

/**
 * 网页面板登录认证 API
 *
 * POST /auth/login   body: {"password":"xxx"}  → {"code":0,"data":{"token":"..."}}
 * POST /auth/verify  body: {"token":"xxx"}     → {"code":0,"data":{"lockTimeoutMs":600000}}
 *
 * @author: midpoint
 * @date: 2026/06/11
 */
@Slf4j
public class AuthHandler extends JSONHandler {

    protected AuthHandler(String prefix) {
        super(prefix);
    }

    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message<Object> msg = new Message<>();

        try {
            if ("login".equals(indexPath)) {
                handleLogin(httpExchange, msg);
            } else if ("verify".equals(indexPath)) {
                handleVerify(httpExchange, msg);
            } else if ("config".equals(indexPath)) {
                handleConfig(msg);
            } else {
                msg.setCode(1);
                msg.setMessage("未知的认证接口");
            }
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("认证处理异常: " + e.getMessage());
            log.error("AuthHandler 异常", e);
        }

        responseJson(httpExchange, msg);
    }

    private void handleLogin(HttpExchange httpExchange, Message<Object> msg) throws IOException {
        String body = parseRequestBodyStr(httpExchange);
        JSONObject json = new JSONObject(body);
        String password = json.getStr("password");

        AuthManager am = AuthManager.getInstance();
        String token = am.login(password);

        if (token != null) {
            JSONObject data = new JSONObject();
            data.set("token", token);
            data.set("lockTimeoutMs", am.getLockTimeoutMs());
            msg.setData(data);
            msg.setMessage("登录成功");
        } else {
            msg.setCode(1);
            msg.setMessage("密码错误");
        }
    }

    private void handleVerify(HttpExchange httpExchange, Message<Object> msg) throws IOException {
        String body = parseRequestBodyStr(httpExchange);
        JSONObject json = new JSONObject(body);
        String token = json.getStr("token");

        AuthManager am = AuthManager.getInstance();
        if (!am.isEnabled()) {
            // 未启用密码保护，直接放行
            JSONObject data = new JSONObject();
            data.set("enabled", false);
            msg.setData(data);
            return;
        }

        if (am.verify(token)) {
            JSONObject data = new JSONObject();
            data.set("lockTimeoutMs", am.getLockTimeoutMs());
            msg.setData(data);
        } else {
            msg.setCode(1);
            msg.setMessage("会话已过期，请重新登录");
        }
    }

    private void handleConfig(Message<Object> msg) {
        AuthManager am = AuthManager.getInstance();
        JSONObject data = new JSONObject();
        data.set("enabled", am.isEnabled());
        data.set("lockTimeoutMs", am.getLockTimeoutMs());
        msg.setData(data);
    }
}
