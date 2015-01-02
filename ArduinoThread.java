import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentLinkedDeque;


/*
  Each ArduinoThread class will make a connection to a server in an arduino, thus
  representing each, a semaphore.
*/
class ArduinoThread extends Thread {
    private final String NEW_LINE = "\n";

    // don't know if needed
    private final String INPUT = "I";
    private final String OUTPUT = "O";

    // ON and OFF are used to check the state of the light in this semaphore
    public static final String ON = "H";
    public static final String OFF = "L";
    private final String STATE = "R";

    public static final int GREEN = 13;
    public static final int YELLOW = 12;
    public static final int RED = 11;
    public static final String CAR_COUNT = "C"; // for questing the number of cars in this semaphore

    private int port;
    private int SEM_ID;
    private String SEM_IP; // ip address of the arduino

    //private ServerSocket sc; // socket that the Arduino is connecting to
    private Socket socket;
    private Socket sc_car_counter;

    private DataOutputStream out; // write to socket
    private BufferedReader in; // read from socket
    private BufferedReader in_car_counter; // read from socket

    public int n_cars;
    public int active_time; // the time since this semaphore was activated
    public boolean has_bottleneck;
    public long bottleneck_t; // this variable will hold a timestamp indicating when "n_cars" >= 10

    public ArduinoThread(String SEM_IP, int port) {
        this.port = port;
        this.SEM_IP = SEM_IP;
        try {
            socket = new Socket(SEM_IP, port);
            //            sc_car_counternew Socket("localhost", 60000);

            System.out.println("# Arduino: started thread...");
            //socket = sc.accept(); // wait for a connection. When connected enter the while loop
            System.out.println("# Arduino: got a connection...");
            out = new DataOutputStream(socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            in_car_counter = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch(Exception e) {
            e.printStackTrace();
        }
        n_cars = 0;
        active_time = 0;
        has_bottleneck = false;
        bottleneck_t = 0;
        start();
        init_lights();
    }

    // Put arduino LED's in output mode
    private void init_lights() {
        try {
        out.writeBytes(OUTPUT + GREEN + NEW_LINE);
        out.writeBytes(OUTPUT + RED + NEW_LINE);
        out.writeBytes(OUTPUT + YELLOW + NEW_LINE);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // request this arduino for an update on the number of cars there
    public void get_cars_count() {
        new Thread() {
            public void run() {
                try {
                    //out.writeBytes(CAR_COUNT + NEW_LINE);
                    String answer;
                    while((answer = in.readLine()) == null);
                    n_cars = Integer.parseInt(answer);
                    if(n_cars >= 10)
                        has_bottleneck = true;
                    
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void set_low(int light) {
        try {
            out.writeBytes(OFF + light + NEW_LINE);

            // if(!change_confirmed(OFF)) {
            //     // exit this function and try again?
            //     System.out.println("# Arduino: request failed");
            // }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void set_high(int light) {
        try {
            // TODO: confirm
            out.writeBytes(ON + light + NEW_LINE);
            
            // if(!change_confirmed(ON)) {
            //     // exit this function and try again?
            //     System.out.println("# Arduino: request failed...");
            // }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    // Return if the request to change semaphore was fullfiled
    // Receives as parameter the expected state of the semaphore
    // E.g, if the arduino was asked to turn off a semaphore, expected state will be OFF
    public boolean change_confirmed(String expected_state) {
        try {
            String msg;
            while((msg = in.readLine()) == null); // obligatory to wait for "ack"
            int return_v = Integer.parseInt(msg.replace(NEW_LINE, "")); // remove "\n" and convert to integer
            if(expected_state == ON && return_v == 1)
                return true;
            else if(expected_state == OFF && return_v == 0)
                return true;

            return false;

        } catch(Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
