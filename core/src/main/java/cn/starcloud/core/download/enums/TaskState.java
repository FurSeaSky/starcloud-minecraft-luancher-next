package cn.starcloud.core.download.enums;

public enum TaskState {
  PENDING, // 在队列等待分配连接
  RUNNING, // 正在传输
  SUCCESS, // 下载+sha1校验通过
  FAILED, // 网络/解析/校验/超时失败
  CANCELLED // 被取消
}
