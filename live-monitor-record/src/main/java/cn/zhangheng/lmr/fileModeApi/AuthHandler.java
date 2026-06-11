package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.json.JSONObject;
import cn.zhangheng.common.httpServer.auth.AuthManager;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 网页面板登录认证 API
 *
 * POST /auth/login   body: {"password":"xxx"}  → {"code":0,"data":{"token":"..."}}
 * POST /auth/verify  body: {"token":"xxx"}     → {"code":0,"data":{"lockTimeoutMs":600000}}
 * POST /auth/config  (no body needed)          → {"code":0,"data":{"enabled":true,"lockTimeoutMs":600000}}
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
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        log.debug("AuthHandler 收到请求: {} {}", httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath());
        return true;
    }

    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        log.info("AuthHandler 处理: indexPath={}", indexPath);
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
                msg.setMessage("未知的认证接口: " + indexPath);
            }
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("认证处理异常: " + e.getMessage());
            log.error("AuthHandler 异常, indexPath={}", indexPath, e);
        }

        log.info("AuthHandler 响应: code={}, message={}", msg.getCode(), msg.getMessage());
        responseJson(httpExchange, msg);
    }

    private void handleLogin(HttpExchange httpExchange, Message<Object> msg) throws IOException {
        String body = parseRequestBodyStr(httpExchange);
        JSONObject json = new JSONObject(body);
        String password = json.getStr("password");

        AuthManager am = AuthManager.getInstance();
        String token = am.login(password);

        if (token != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("lockTimeoutMs", am.getLockTimeoutMs());
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
            Map<String, Object> data = new HashMap<>();
            data.put("enabled", false);
            msg.setData(data);
            return;
        }

        if (am.verify(token)) {
            Map<String, Object> data = new HashMap<>();
            data.put("lockTimeoutMs", am.getLockTimeoutMs());
            msg.setData(data);
        } else {
            msg.setCode(1);
            msg.setMessage("会话已过期，请重新登录");
        }
    }

    private void handleConfig(Message<Object> msg) {
        AuthManager am = AuthManager.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", am.isEnabled());
        data.put("lockTimeoutMs", am.getLockTimeoutMs());
        msg.setData(data);
        log.info("AuthHandler config: enabled={}, lockTimeoutMs={}", am.isEnabled(), am.getLockTimeoutMs());
    }
}
