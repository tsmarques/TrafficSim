import java.util.Scanner;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;

public class TrafficSim {
        private static final int MAX_TIME = 15; // maximum time a group of semaphores can be active
    private static final int MAX_BOTT = 10; // maximum time allowed for a bottleneck
    private static final int N_SEM = 4; // number of semaphores

    private static ArduinoThread[] semaphores;
    private static String IP[] = {"192.168.10.81", "192.168.10.82", "192.168.10.79", "192.168.10.77"};
    
    private static int next_active = 2; // next group of semaphore to be activated. Can have value 1 or 2
    private static double start_t; // time when a group of semaphores was activated
    private static double last_update_t; // time since last car update from the arduinos
    
    private static void init_devices() {
        semaphores = new ArduinoThread[N_SEM];
        for(int i = 0; i < N_SEM; i++)
            semaphores[i] = new ArduinoThread("localhost", 53333 + i);
    }

    /* Sets the initial state of the simulation with group 1 as ON*/
    static void init_semaphores() {
        semaphores[0].set_high(ArduinoThread.GREEN);
        semaphores[1].set_high(ArduinoThread.GREEN);
        
        semaphores[2].set_high(ArduinoThread.RED);
        semaphores[3].set_high(ArduinoThread.RED);
    }

    private static void semaphores_to_yellow() {
        /* turn off current group*/
        if(next_active == 1) {
            semaphores[2].set_low(ArduinoThread.GREEN);
            semaphores[3].set_low(ArduinoThread.GREEN);
            semaphores[2].set_high(ArduinoThread.YELLOW);
            semaphores[3].set_high(ArduinoThread.YELLOW);
        }
        
        else if(next_active == 2) {
            semaphores[0].set_low(ArduinoThread.GREEN);
            semaphores[1].set_low(ArduinoThread.GREEN);
            semaphores[0].set_high(ArduinoThread.YELLOW);
            semaphores[1].set_high(ArduinoThread.YELLOW);
        }
    }
    
    /*
      A semaphore group is made of 2 semaphores and 2 crosswalks.
      Groups id's are 1 and 2.
      Group 1 is made of semaphores 0 & 1 and crosswalks 0 & 1
      Group 2 is made of semaphores 2 & 3 and crosswalks 2 & 3
      If variable "next_active" == 1, then the current active
      group is 0, and vice versa.
     */
    private static void change_semaphores() {
        if(next_active == 1) {
            semaphores[2].set_low(ArduinoThread.YELLOW);
            semaphores[3].set_low(ArduinoThread.YELLOW);
            semaphores[2].set_high(ArduinoThread.RED);
            semaphores[3].set_high(ArduinoThread.RED);

            /* turn on next group */
            semaphores[0].set_low(ArduinoThread.RED);
            semaphores[1].set_low(ArduinoThread.RED);
            semaphores[0].set_high(ArduinoThread.GREEN);
            semaphores[1].set_high(ArduinoThread.GREEN);
        }

        else if(next_active == 2) {
            semaphores[0].set_low(ArduinoThread.YELLOW);
            semaphores[1].set_low(ArduinoThread.YELLOW);
            semaphores[0].set_high(ArduinoThread.RED);
            semaphores[1].set_high(ArduinoThread.RED);

            /* turn on next group */
            semaphores[2].set_low(ArduinoThread.RED);
            semaphores[3].set_low(ArduinoThread.RED);
            semaphores[2].set_high(ArduinoThread.GREEN);
            semaphores[3].set_high(ArduinoThread.GREEN);
        }

        start_t = System.currentTimeMillis();
        
        if(next_active == 1)
            next_active++;
        else
            next_active--;
    }

    // checks if in any of the semaphores that are off, there're
    // more than 10 cars waiting. If yes, returns the index of
    // the semaphore that has a bottleneck. If both have send the
    // one whose bottleneck is "older"
    private static int exists_bottleneck() {
        int off_1 = next_active;
        int off_2;
        if(next_active == 1)
            off_2 = next_active - 1;
        else
            off_2 = next_active + 1;
        
        if(semaphores[off_1].has_bottleneck && !semaphores[off_2].has_bottleneck)
            return off_1;
        else if(semaphores[off_2].has_bottleneck && !semaphores[off_1].has_bottleneck)
            return off_2;
        else if(semaphores[off_1].has_bottleneck && semaphores[off_2].has_bottleneck) {
            if(semaphores[off_1].bottleneck_t >= semaphores[off_2].bottleneck_t)
                return off_1;
            else
                return off_2;
        }
        
        return -1;
    }

    /* Get an update of the number of cars in each semaphore and send it to the arduinos */
    private static void update_nr_cars() {
        for(int i = 0; i < N_SEM; i++) {
            semaphores[i].get_cars_count();
            System.out.println("# There's " + semaphores[i].n_cars + " cars in semaphore " + i);
        }
        System.out.println();
    }
    // Main function
    private static void start_sim() {
        System.out.println("# Starting Simulation...");
        System.out.println("Group " + (next_active - 1) + " is active");
        
        init_semaphores();
        
        double curr_t; // time at this iteration
        start_t = System.currentTimeMillis();
        while(true) {
            curr_t = System.currentTimeMillis();
            /* Get update regarding nr of cars, every 5 seconds */
            if((curr_t - last_update_t) / 1000.0 >= 5) {
                update_nr_cars();
                last_update_t = curr_t;
            }
            int bottleneck = -1;
            
            if((bottleneck = exists_bottleneck()) != -1) { // if there's a bottleneck
                System.out.println("# BOTTLENECK");
                if((curr_t - semaphores[bottleneck].bottleneck_t) >= MAX_BOTT) { // if bottleneck has been waiting for 10 seconds
                    System.out.println("# CRITICAL");
                    semaphores[bottleneck].has_bottleneck = false;
                    semaphores[bottleneck].bottleneck_t = -1;
                    change_semaphores();
                }
                else { // bottleneck not critical yet
                    System.out.println("# NOT CRITICAL");
                    if((curr_t - start_t) / 1000 == MAX_TIME - 5.00) {
                        System.out.println("# Changing to yellow");
                        semaphores_to_yellow();
                    }
                    else if((curr_t - start_t) / 1000.0 == MAX_TIME) { // time to change semaphores
                        System.out.println("# NOT CRITICAL");
                        System.out.println("# Group " + next_active + " is active...");
                        change_semaphores();
                    }
                }
            }
            else { // no bottleneck
                if((curr_t - start_t) / 1000.0 == MAX_TIME - 5.00) {
                    System.out.println("# Changing to yellow");
                    semaphores_to_yellow();                    
                }
                else if((curr_t - start_t) / 1000.0 == MAX_TIME) { // time to change semaphores
                    System.out.println("# Group " + next_active + " is active...");
                    change_semaphores();
                }
            }
        }
    }
    
    public static void main() {
        init_devices();
        start_sim();
    }
}
