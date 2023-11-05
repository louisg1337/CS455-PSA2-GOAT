import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     * int MAXDATASIZE : the maximum size of the Message data and
     * Packet payload
     *
     * int A : a predefined integer that represents entity A
     * int B : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     * void stopTimer(int entity):
     * Stops the timer running at "entity" [A or B]
     * void startTimer(int entity, double increment):
     * Starts a timer running at "entity" [A or B], which will expire in
     * "increment" time units, causing the interrupt handler to be
     * called. You should only call this with A.
     * void toLayer3(int callingEntity, Packet p)
     * Puts the packet "p" into the network from "callingEntity" [A or B]
     * void toLayer5(String dataSent)
     * Passes "dataSent" up to layer 5
     * double getTime()
     * Returns the current time in the simulator. Might be useful for
     * debugging.
     * int getTraceLevel()
     * Returns TraceLevel
     * void printEventList()
     * Prints the current event list to stdout. Might be useful for
     * debugging, but probably not.
     *
     *
     * Predefined Classes:
     *
     * Message: Used to encapsulate a message coming from layer 5
     * Constructor:
     * Message(String inputData):
     * creates a new Message containing "inputData"
     * Methods:
     * boolean setData(String inputData):
     * sets an existing Message's data to "inputData"
     * returns true on success, false otherwise
     * String getData():
     * returns the data contained in the message
     * Packet: Used to encapsulate a packet
     * Constructors:
     * Packet (Packet p):
     * creates a new Packet that is a copy of "p"
     * Packet (int seq, int ack, int check, String newPayload)
     * creates a new Packet with a sequence field of "seq", an
     * ack field of "ack", a checksum field of "check", and a
     * payload of "newPayload"
     * Packet (int seq, int ack, int check)
     * chreate a new Packet with a sequence field of "seq", an
     * ack field of "ack", a checksum field of "check", and
     * an empty payload
     * Methods:
     * boolean setSeqnum(int n)
     * sets the Packet's sequence field to "n"
     * returns true on success, false otherwise
     * boolean setAcknum(int n)
     * sets the Packet's ack field to "n"
     * returns true on success, false otherwise
     * boolean setChecksum(int n)
     * sets the Packet's checksum to "n"
     * returns true on success, false otherwise
     * boolean setPayload(String newPayload)
     * sets the Packet's payload to "newPayload"
     * returns true on success, false otherwise
     * int getSeqnum()
     * returns the contents of the Packet's sequence field
     * int getAcknum()
     * returns the contents of the Packet's ack field
     * int getChecksum()
     * returns the checksum of the Packet
     * int getPayload()
     * returns the Packet's payload
     *
     */

    /*
     * Please use the following variables in your routines.
     * int WindowSize : the window size
     * double RxmtInterval : the retransmission timeout
     * int LimitSeqNo : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here. Remember, you cannot use
    // these variables to send messages error free! They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    private int seqIndexA = 0;
    private Queue<Message> buffer = new LinkedList<>();
    private int[] windowA;
    private int[] windowTrackerA;
    private Message[] messageTracker;

    private int[] windowB;
    private int[] windowTrackerB;

    protected int checkSum(int seq, int ack, String newPayload) {
        int total = 0;
        total += seq;
        total += ack;

        if (newPayload.length() == 0) {
            return total;
        }

        for (int i = 0; i < newPayload.length(); i++) {
            char ch = newPayload.charAt(i);
            total += (int) ch;
        }

        return total;
    }

    // Shifts the window over
    // i.e. [0,1,2,3] -> [1,2,3,4]
    public int[] shiftWindow(int[] window) {
        int[] newArray = new int[this.WindowSize];

        int x = 0;
        for (int i = window[0] + 1; i < window[0] + 1 + this.WindowSize; i++) {
            newArray[x] = i % (this.WindowSize + 1);
            x++;
        }

        return newArray;
    }

    // Shifts the tracker over
    // i.e. [1,0,1,1] -> [0,1,1,0]
    public int[] shiftTracker(int[] tracker) {
        int prev = 0;
        for (int i = this.WindowSize - 1; i >= 0; i--) {
            int temp = tracker[i];
            tracker[i] = prev;
            prev = temp;
        }

        return tracker;
    }

    // Shift message tracker over
    // Used to store previous messages in case we need to retransmit
    public Message[] shiftMessageTracker(Message[] tracker) {
        Message prev = new Message("");
        for (int i = tracker.length - 1; i >= 0; i--) {
            Message temp = tracker[i];
            tracker[i] = prev;
            prev = temp;
        }
        return tracker;
    }

    // Takes in the seqNumber sent, and finds the corresponding index in window
    // Use this so that we can keep windowTracker updated to know which packets were
    // received
    public int seqNumToIndex(int[] window, int seq) {
        for (int i = 0; i < window.length; i++) {
            if (window[i] == seq) {
                return i;
            }
        }

        return -1;
    }

    // Determines how many times we can shift over the window given what packets
    // were acked
    public int numOfShift(int[] tracker) {
        int shift = 0;
        for (int i = 0; i < tracker.length; i++) {
            if (tracker[i] == 1) {
                shift++;
            } else {
                return shift;
            }
        }
        return shift;
    }

    // This is the constructor. Don't touch!
    public StudentNetworkSimulator(int numMessages,
            double loss,
            double corrupt,
            double avgDelay,
            int trace,
            int seed,
            int winsize,
            double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send. It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        // Add the packet to the buffer, want to control what we are sending
        buffer.add(message);

        // Check to see if we have any space left in window to send packets
        if (seqIndexA < this.WindowSize) {
            // Get data ready
            Message currentMessage = buffer.remove();
            int check = checkSum(windowA[seqIndexA], 0, currentMessage.getData());
            Packet packet = new Packet(windowA[seqIndexA], 0, check, currentMessage.getData());
            // Save message in case we need to retransmit it
            messageTracker[seqIndexA] = currentMessage;
            // Increase the index
            seqIndexA++;
            toLayer3(0, packet);
        }

    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side. "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
        System.out.println("");
        System.out.println("///////////////////////////////");
        System.out.println("A INPUT: Received this packet...");
        System.out.println(packet.toString());
        System.out.println("-----------------");
        System.out.println("windowA START: " + Arrays.toString(windowA));
        System.out.println("windowTrackerA START: " + Arrays.toString(windowTrackerA));
        System.out.println("messageTracker START: " + Arrays.toString(messageTracker));

        // Check the checksum
        int check = checkSum(packet.getSeqnum(), packet.getAcknum(), packet.getPayload());
        if (check != packet.getChecksum()) {
            // Drop the packet
            return;
        }

        // Update A's windows to reflect the ACKed packet
        // Add to tracker
        int trackerIndex = seqNumToIndex(windowA, packet.getAcknum());
        if (trackerIndex == -1) {
            System.out.println("PACKET OUT OF BOUNDS IN A INPUT");
            return;
        }
        windowTrackerA[trackerIndex] = 1;

        // Check if we need to shift the window at all
        int shift = numOfShift(windowTrackerA);

        if (shift > 0) {
            int[] newWindowA = windowA;
            int[] newWindowTrackerA = windowTrackerA;
            Message[] newMessageTracker = messageTracker;
            for (int i = 0; i < shift; i++) {
                newWindowA = shiftWindow(newWindowA);
                newWindowTrackerA = shiftTracker(newWindowTrackerA);
                newMessageTracker = shiftMessageTracker(newMessageTracker);
                seqIndexA = seqIndexA - 1;
            }
            // Adjust values
            windowA = newWindowA;
            newWindowTrackerA = windowTrackerA;
        }

        System.out.println("-----------------");
        System.out.println("windowA END: " + Arrays.toString(windowA));
        System.out.println("windowTrackerA END: " + Arrays.toString(windowTrackerA));
        System.out.println("messageTracker END: " + Arrays.toString(messageTracker));
        System.out.println("-----------------");

    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("");
        System.out.println("A TIMER INTERRUPT");

    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        windowTrackerA = new int[this.WindowSize];
        windowA = new int[this.WindowSize];
        for (int i = 0; i < this.WindowSize; i++) {
            windowA[i] = i;
        }

        messageTracker = new Message[this.WindowSize];
        for (int x = 0; x < this.WindowSize; x++) {
            messageTracker[x] = new Message("");
        }

    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side. "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {
        System.out.println("");
        System.out.println("///////////////////////////////");
        System.out.println("B INPUT: Received this packet...");
        System.out.println(packet.toString());
        System.out.println("-----------------");
        System.out.println("windowB START: " + Arrays.toString(windowB));
        System.out.println("windowTrackerB START: " + Arrays.toString(windowTrackerB));

        // Check the checksum
        int check = checkSum(packet.getSeqnum(), packet.getAcknum(), packet.getPayload());
        if (check != packet.getChecksum()) {
            // Drop the packet
            return;
        }

        // Add to tracker
        int trackerIndex = seqNumToIndex(windowB, packet.getSeqnum());
        if (trackerIndex == -1) {
            System.out.println("PACKET OUT OF BOUNDS IN B INPUT");
            return;
        }
        windowTrackerB[trackerIndex] = 1;

        // Check if we need to shift the window at all
        int shift = numOfShift(windowTrackerB);

        if (shift > 0) {
            int[] newWindowB = windowB;
            int[] newWindowTrackerB = windowTrackerB;
            for (int i = 0; i < shift; i++) {
                newWindowB = shiftWindow(newWindowB);
                newWindowTrackerB = shiftTracker(newWindowTrackerB);
            }
            windowB = newWindowB;
            newWindowTrackerB = windowTrackerB;
        }

        System.out.println("-----------------");
        System.out.println("windowB END: " + Arrays.toString(windowB));
        System.out.println("windowTrackerB END: " + Arrays.toString(windowTrackerB));
        System.out.println("-----------------");

        // Send ack back
        Packet ackPacket = new Packet(0, packet.getSeqnum(), packet.getSeqnum());
        toLayer3(1, ackPacket);

    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        windowTrackerB = new int[this.WindowSize];
        windowB = new int[this.WindowSize];
        for (int i = 0; i < this.WindowSize; i++) {
            windowB[i] = i;
        }
    }

    // Use to print final statistics
    protected void Simulation_done() {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO
        // NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + "<YourVariableHere>");
        System.out.println("Number of retransmissions by A:" + "<YourVariableHere>");
        System.out.println("Number of data packets delivered to layer 5 at B:" + "<YourVariableHere>");
        System.out.println("Number of ACK packets sent by B:" + "<YourVariableHere>");
        System.out.println("Number of corrupted packets:" + "<YourVariableHere>");
        System.out.println("Ratio of lost packets:" + "<YourVariableHere>");
        System.out.println("Ratio of corrupted packets:" + "<YourVariableHere>");
        System.out.println("Average RTT:" + "<YourVariableHere>");
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        // System.out.println("Example statistic you want to check e.g. number of ACK
        // packets received by A :" + "<YourVariableHere>");
    }

}
