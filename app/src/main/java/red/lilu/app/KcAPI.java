package red.lilu.app;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

import kc.Kc;

public class KcAPI {

    private static final String T = "调试";

    public static class State {
        int peerCount;
        int connCount;
    }

    public static class Option {
        String name = "";
        String photo = "";
        LinkedList<String> bootstrap_array = new LinkedList<>();
        LinkedList<String> blacklist_id_array = new LinkedList<>();
    }

    public static class Contact {
        String id;
        String name;
        String photo;
        String nameRemark;
    }

    public static class ChatMessage {
        long id;
        String fromPeerID;
        String toPeerID;
        String text;
        long fileSize;
        String fileNameWithoutExtension;
        String fileExtension;
        String state;
        boolean read;
    }

    public static class RemoteControlInfo {
        int width;
        int height;

        public RemoteControlInfo(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * 获取文件目录
     */
    public static File getFileDirectory(MyApplication myApplication) {
        return myApplication.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
    }

    /**
     * 获取设置
     */
    public static void getOption(MyApplication application,
                                 java.util.function.Consumer<String> onError,
                                 java.util.function.Consumer<Option> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Option option = application.getGson().fromJson(
                        Kc.getOption(),
                        new TypeToken<Option>() {
                        }.getType()
                );

                onDone.accept(option);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, application.getExecutorService());
    }

    /**
     * 设置我的名字和头像
     */
    public static void setInfo(String name, String photo,
                               MyApplication myApplication,
                               java.util.function.Consumer<String> onError,
                               java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.setInfo(name, photo);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 设置超级破址
     */
    public static void setOptionBootstrap(String arrayText,
                                          MyApplication myApplication,
                                          java.util.function.Consumer<String> onError,
                                          java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.setBootstrapArray(arrayText);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 设置黑名单
     */
    public static void setOptionBlacklistID(String arrayText,
                                            MyApplication myApplication,
                                            java.util.function.Consumer<String> onError,
                                            java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.setBlacklistIDArray(arrayText);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 获取联系人Map
     */
    public static void getContact(MyApplication myApplication,
                                  java.util.function.Consumer<String> onError,
                                  java.util.function.Consumer<HashMap<String, Contact>> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                HashMap<String, Contact> map = myApplication.getGson().fromJson(
                        Kc.getContact(),
                        new TypeToken<HashMap<String, Contact>>() {
                        }.getType()
                );

                onDone.accept(map);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 添加(更新)联系人
     *
     * @param peerID 节点ID
     */
    public static void newContact(String peerID,
                                  MyApplication myApplication,
                                  java.util.function.Consumer<String> onError,
                                  java.util.function.Consumer<Contact> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Contact contact = myApplication.getGson().fromJson(
                        Kc.newContact(peerID),
                        new TypeToken<Contact>() {
                        }.getType()
                );

                onDone.accept(contact);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 删除联系人
     */
    public static void delContact(String peerID,
                                  MyApplication myApplication,
                                  java.util.function.Consumer<String> onError,
                                  java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.delContact(peerID);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 设置联系人名字备注
     */
    public static void setContactNameRemark(String id, String nameRemark,
                                            MyApplication myApplication,
                                            java.util.function.Consumer<String> onError,
                                            java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.setContactNameRemark(id, nameRemark);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 发送会话消息文本
     */
    public static void sendChatMessageText(String peerID, String text,
                                           MyApplication myApplication,
                                           java.util.function.Consumer<String> onError,
                                           java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.sendChatMessageText(peerID, text);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 发送会话消息文件
     */
    public static void sendChatMessageFile(String peerID, MyApplication.FileInfo fileInfo,
                                           MyApplication myApplication,
                                           java.util.function.Consumer<String> onError,
                                           java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.sendChatMessageFile(peerID, fileInfo.nameWithoutExtension, fileInfo.extension, fileInfo.size);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 查询会话消息
     */
    public static void findChatMessage(String peerID,
                                       MyApplication myApplication,
                                       java.util.function.Consumer<String> onError,
                                       java.util.function.Consumer<ArrayList<ChatMessage>> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                ArrayList<ChatMessage> data = myApplication.getGson().fromJson(
                        Kc.findChatMessage(peerID),
                        new TypeToken<ArrayList<ChatMessage>>() {
                        }.getType()
                );
                onDone.accept(data);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 通过节点ID设置会话消息已读
     */
    public static void setChatMessageReadByPeerID(String peerID,
                                                  MyApplication myApplication,
                                                  java.util.function.Consumer<String> onError,
                                                  java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.setChatMessageReadByPeerID(peerID);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 通过节点ID删除会话消息
     */
    public static void deleteChatMessageByPeerID(String peerID,
                                                 MyApplication myApplication,
                                                 java.util.function.Consumer<String> onError,
                                                 java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.deleteChatMessageByPeerID(peerID);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 通过ID删除会话消息
     */
    public static void deleteChatMessageByID(long id,
                                             MyApplication myApplication,
                                             java.util.function.Consumer<String> onError,
                                             java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.deleteChatMessageByID(id);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 获取连接状态节点ID
     */
    public static void getConnectedPeerIDs(MyApplication myApplication,
                                           java.util.function.Consumer<String> onError,
                                           java.util.function.Consumer<ArrayList<String>> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                ArrayList<String> list = myApplication.getGson().fromJson(
                        Kc.getConnectedPeerIDs(),
                        new TypeToken<ArrayList<String>>() {
                        }.getType()
                );

                onDone.accept(list);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 获取未读会话消息数量
     */
    public static void getChatMessageUnReadCount(MyApplication myApplication,
                                                 java.util.function.Consumer<String> onError,
                                                 java.util.function.Consumer<HashMap<String, Long>> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                HashMap<String, Long> map = myApplication.getGson().fromJson(
                        Kc.getChatMessageUnReadCount(),
                        new TypeToken<HashMap<String, Long>>() {
                        }.getType()
                );

                onDone.accept(map);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 请求远程控制
     *
     * @param id 节点ID
     */
    public static void requestRemoteControl(String id,
                                            MyApplication myApplication,
                                            java.util.function.Consumer<String> onError,
                                            java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.requestRemoteControl(id); // TODO 存在错误

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 允许远程控制
     *
     * @param info 为空表示拒绝
     */
    public static void allowRemoteControl(@Nullable RemoteControlInfo info,
                                          MyApplication myApplication,
                                          java.util.function.Consumer<String> onError,
                                          java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                String json = "";
                if (info != null) {
                    json = myApplication.getGson().toJson(info);
                }
                Kc.allowRemoteControl(json);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

    /**
     * 发送远程控制数据
     */
    public static void sendRemoteControlVideoData(long presentationTimeUs, byte[] data,
                                                  MyApplication myApplication,
                                                  java.util.function.Consumer<String> onError,
                                                  java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Kc.sendRemoteControlVideoData(presentationTimeUs, data);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, myApplication.getExecutorService());
    }

}
