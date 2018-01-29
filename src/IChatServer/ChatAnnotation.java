package IChatServer ;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;


@ServerEndpoint(value = "/websocket/chat")
public class ChatAnnotation {
    static PrintStream Console = System.out;
    /*
     * 用与服务器与客户端之间的指令传输
     * 所有指令都用^进行分割，方便split()
     */
    public static String [] command = {
            "LIST" ,            // 返回当前服务器在线人员的列表
            "LOGIN" ,           // 登陆服务器，需要修改列表，并对聊天室进行广播
            "WIDE" ,            // 用户向服务器发送广播消息
            "SOLO" ,            // 用户向用户单独发送消息
            "JOIN" ,            // 用户加入当前聊天室
            "EXIT" ,            // 用户退出当前聊天室
            "USER" ,            // 返回指定用户信息
            "SERVE" ,           // 用于客户与客服之间的往返
            "SLIST" ,           // 返回当前客服列表
            "IMG"               // 用于发送表情或者图片
    };

    private static final String GUEST_PREFIX = "Guest";

    private static final AtomicInteger connectionIds = new AtomicInteger(0);
    private static final Set<ChatAnnotation> connections =
            new CopyOnWriteArraySet<ChatAnnotation>();
    private static final Set<ChatAnnotation> need_serve =new CopyOnWriteArraySet<ChatAnnotation>();

    private String nickname;
    private String imgurl = "";
    private Session session;

    public ChatAnnotation() {
        nickname = GUEST_PREFIX + connectionIds.getAndIncrement();
    }
    public ChatAnnotation(String name , String url){
        nickname =  name ;
        imgurl = url ;
    }
    /*
        HttpSession session = request.getSession();
        session.setAttribute("变量名", 值对象);
        session.getAttribute("变量名"); //此时取出来的是Object, 一般需要强转
        session.removeAttribute("变量名");
        session.invalidate(); //删除所有session中保存的键
     */

    @OnOpen
    public void start(Session session ) {
        this.session = session;
        connections.add(this);
        String message = String.format("* %s %s", nickname, "has joined.");
        //broadcast("WIDE^" + message);

    }


    @OnClose
    public void end() {
        connections.remove(this);
        String message = String.format("EXIT^" + "* %s %s",
                nickname, "has disconnected.");
        broadcast(message);
        String temp = "";

        for(ChatAnnotation client : connections){
            if(client.nickname.contains("客服")) continue;
            temp += client.nickname + "^" + client.imgurl + '^';
        }
        temp = "LIST^" + temp ;
        Console.println( temp);
        broadcast("LIST^" + temp);
    }

    /*
     * 从客户端收到信息
     */
    @OnMessage
    public void incoming(String message) {

        if(!check_CMD(message)){
            Console.println("wrong message");
            return;
        }
        Console.println(message);
        // “^”要用转译符号转译
        String array [] = message.split("\\^") ;
        if(message.startsWith("LIST")){
            String temp = "";

            for(ChatAnnotation client : connections){
                if(client.nickname.contains("客服")) continue;
                temp += client.nickname + "^" + client.imgurl + '^';
            }
            temp = "LIST^" + temp ;
            Console.println( temp);
            broadcast("LIST^" + temp);
            return;
        }
        if(message.startsWith("USER")){
            nickname = array[1] ;
            imgurl = array[2] ;
            String m = String.format("* %s %s", nickname, "has joined.");
            if(nickname.startsWith("客服")){
                work_on_SLIST();
                return ;
            }
            broadcast("JOIN^" + m);
            return;
        }
        /*
         * Never trust the client
         * 防止语句注入
         */
        if(message.startsWith("SOLO")){
            // SOLO^送到的人^内容
            for (ChatAnnotation client : connections) {
                if(client.nickname.equals(array[1])){
                    //发给对方的是SOLO^发送者即当前进程^内容
                    try {
                        client.session.getBasicRemote().sendText("SOLO^" + nickname + '^' +imgurl + '^' + array[2]);
                        Console.println("SOLO^" + nickname + '^' +imgurl + '^' + array[2]);
                    }catch (Exception e) {
                        Console.println("error");
                    }
                    return ;
                }
            }

        }
        if(message.startsWith("SLIST")){
            String temp = "";

            for(ChatAnnotation client : connections){
                if(client.nickname.contains("客服"))
                    temp += client.nickname + "^" + client.imgurl + '^';
            }
            temp = "SLIST^" + temp ;
            Console.println( temp);
            try{
                this.session.getBasicRemote().sendText(temp);
            }catch ( Exception e){
                Console.println("SLIST error ;");
            }
            return;
        }
        if(message.startsWith("SERVE")){
            //SERVE^用户^用户头像连接地址^客服^语句
            String name = array[1] ;
            String kefu = array[3] ;
            need_serve.add(new ChatAnnotation(name , array[2])) ;
            boolean flag = false ;
            for (ChatAnnotation client : connections) {
                //分类
                if(this.nickname.contains("客服") && client.nickname.equals(name)){
                    //客服发给用户的
                    //发给对方的是SERVE^客服^语句
                    flag = true ;
                    try {
                        client.session.getBasicRemote().sendText("SERVE^" + kefu + "^" + array[4]);
                        Console.println("SERVE^" + kefu + "^" + array[4]);
                    }catch (Exception e) {
                        Console.println("error");
                    }
                    //return;
                }else if(!this.nickname.contains("客服") && client.nickname.equals(kefu)){
                    //用户发给客服的
                    //发给对方的是SERVE^用户^用户头像连接地址^客服^语句
                    try {
                        flag = true ;
                        client.session.getBasicRemote().sendText(message);
                        Console.println(message);
                        return ;
                    }catch (Exception e) {
                        Console.println("error");
                    }finally {

                    }
                    return;
                }
            }
            if(flag == true) return;
            //如果客服下线了
            //就发给另一个客服
            for(ChatAnnotation cli : connections){
                if(cli.nickname.contains("客服")  ){
                    try{
                        cli.session.getBasicRemote().sendText(message);
                        Console.println(message);
                    }catch (Exception ex){
                        Console.println("error");
                    }
                }
            }
            return;
        }
        String filteredMessage =
                nickname +'^' + HTMLFilter.filter(array[1].toString()) + '^' + imgurl;
        Console.println(filteredMessage);

        if(message.startsWith("WIDE"))
            broadcast("WIDE^" + filteredMessage);
    }




    @OnError
    public void onError(Throwable t) throws Throwable {

    }

    /*
     * 广播，将消息发送给所有用户
     */
    private static void broadcast(String msg) {
        for (ChatAnnotation client : connections) {
            try {
                synchronized (client) {
                    client.session.getBasicRemote().sendText(msg);
                }
            } catch (IOException e) {

                connections.remove(client);
                try {
                    client.session.close();
                } catch (IOException e1) {
                    // Ignore
                }
                String message = String.format("* %s %s",
                        client.nickname, "has been disconnected.");
                broadcast("EXIT^" + message);
                String temp = "";

                for(ChatAnnotation cli : connections){
                    if(cli.nickname.contains("客服")) continue;
                    temp += cli.nickname + "^" + client.imgurl + '^';
                }
                temp = "LIST^" + temp ;
                Console.println( temp);
                broadcast("LIST^" + temp);
            }
        }
    }

    /*
     * 判断从客户端发出的消息是否为完整信息，即前缀是否带预先设立的指令
     */
    public boolean check_CMD(String message){
        for(int i = 0 ; i < command.length ; i ++ ){
            if(message.startsWith(command[i]))
                return true ;
        }
        return false ;
    }
    /*
     * 处理SLIST信息
     */
    public void work_on_SLIST(){
        String temp = "";

        for(ChatAnnotation client : connections){
            if(client.nickname.contains("客服"))
                temp += client.nickname + "^" + client.imgurl + '^';
        }
        temp = "SLIST^" + temp ;
        Console.println( temp);
        for (ChatAnnotation client : connections) {
            try {
                client.session.getBasicRemote().sendText(temp);
            } catch (Exception e) {
                Console.println("SLIST error ;");
            }
        }
    }
}
