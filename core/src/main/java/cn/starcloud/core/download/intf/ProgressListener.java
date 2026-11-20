package cn.starcloud.core.download.intf;

public interface ProgressListener {
  void onProgressUpdate(long progress, long total);
}
