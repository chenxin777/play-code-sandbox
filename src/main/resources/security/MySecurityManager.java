import java.security.Permission;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/19 22:34
 * @modify
 */
public class MySecurityManager extends SecurityManager{

    @Override
    public void checkPermission(Permission perm) {
    }

    @Override
    public void checkRead(String file) {
        //throw new SecurityException("checkRead权限异常: " + file);
    }

    @Override
    public void checkWrite(String file) {
        //throw new SecurityException("checkWrite权限异常: " + file);
    }

    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec权限异常: " + cmd);
    }

    @Override
    public void checkDelete(String file) {
        //throw new SecurityException("checkDelete权限异常: " + file);
    }

    @Override
    public void checkConnect(String host, int port) {
        //throw new SecurityException("checkConnect权限异常: " + host + ":" + port);
    }
}
