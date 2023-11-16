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

    // Stats
    private int numRetransmit = 0;
    private int numSent = 0;
    private int numLayer5 = 0;
    private int numAckPackets = 0;
    private int corrupedPackets = 0;
    private double sumRTT = 0.0;
    private double sumCommunication = 0.0;

    private int seqIndexA = 0;
    private Queue<Message> buffer = new LinkedList<>();
    private int[] windowA;
    private int[] windowTrackerA;
    private String[] messageTracker;
    private double[] timerWindowA;

    private int[] windowB;
    private int[] windowTrackerB;
    private String[] messageTrackerB;
    private String currentB = "";

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
    public String[] shiftMessageTracker(String[] tracker) {
        String prev = "";
        for (int i = this.WindowSize - 1; i >= 0; i--) {
            String temp = tracker[i];
            tracker[i] = prev;
            prev = temp;
        }
        return tracker;
    }

    // Helper function to shift over times packages were sent
    public double[] shiftTimerWindow(double[] tracker) {
        double prev = 0.0;
        for (int i = this.WindowSize - 1; i >= 0; i--) {
            double temp = tracker[i];
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
            if (seqIndexA == 0) {
                startTimer(0, RxmtInterval);
            }

            // Get data ready
            Message currentMessage = buffer.remove();
            String stringMessage = currentMessage.getData();
            int check = checkSum(windowA[seqIndexA], 0, stringMessage);
            Packet packet = new Packet(windowA[seqIndexA], 0, check, stringMessage);

            // Save message in case we need to retransmit it
            messageTracker[seqIndexA] = stringMessage;

            // Save time sent
            timerWindowA[seqIndexA] = getTime();

            // Increase the index
            seqIndexA++;

            System.out.println("");
            System.out.println("///////////////////////////////");
            System.out.println("A OUTPUT: Sending this packet...");
            System.out.println(packet.toString());

            numSent++;
            toLayer3(0, packet);
            // startTimer(0, RxmtInterval);
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
            corrupedPackets++;
            System.out.println("CORRUPT PACKET IN A, DROPPING");
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
            // If we are shifting the window, then reset the timer
            stopTimer(0);

            int[] newWindowA = windowA;
            int[] newWindowTrackerA = windowTrackerA;
            String[] newMessageTracker = messageTracker;
            double[] newTimerWindowA = timerWindowA;
            for (int i = 0; i < shift; i++) {
                // Save time taken
                if (timerWindowA[0] < 0) {
                    // If negative value is stored, that means that it was a retransmitted value
                    sumCommunication += (getTime() - (-1.0 * timerWindowA[0]));
                } else {
                    // Normal value
                    sumRTT += (getTime() - timerWindowA[0]);
                }

                newWindowA = shiftWindow(newWindowA);
                newWindowTrackerA = shiftTracker(newWindowTrackerA);
                newMessageTracker = shiftMessageTracker(newMessageTracker);
                newTimerWindowA = shiftTimerWindow(newTimerWindowA);
                seqIndexA = Math.max(0, seqIndexA - 1);
            }
            // Adjust values
            windowA = newWindowA;
            windowTrackerA = newWindowTrackerA;
            messageTracker = newMessageTracker;
            timerWindowA = newTimerWindowA;
        }

        System.out.println("-----------------");
        System.out.println("windowA END: " + Arrays.toString(windowA));
        System.out.println("windowTrackerA END: " + Arrays.toString(windowTrackerA));
        System.out.println("messageTracker END: " + Arrays.toString(messageTracker));
        System.out.println("///////////////////////////////");
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("");
        System.out.println("//////////////////");
        System.out.println("A TIMER INTERRUPT");

        // // Check to see if need to retransmit anything
        // boolean test = false;
        // for (int x = 0; x < windowTrackerA.length; x++){
        // if (windowTrackerA[x] == )
        // }

        for (int i = 0; i < windowTrackerA.length; i++) {
            // If 0 then has not been acked in window yet
            if (windowTrackerA[i] == 0 && messageTracker[i] != "") {
                // Prepare packet for retransmission
                String payload = messageTracker[i];
                int check = checkSum(windowA[i], 0, payload);
                Packet retransmit = new Packet(windowA[i], 0, check, payload);
                System.out.println("Retransmitting... " + retransmit.toString());

                // Set variables to help with stats
                numRetransmit += 1;
                timerWindowA[i] = Math.min((timerWindowA[i] * -1.0), timerWindowA[i]);

                toLayer3(0, retransmit);
            }
        }

        // Retransmit outstanding packets
        startTimer(0, RxmtInterval);

    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        windowTrackerA = new int[this.WindowSize];
        windowA = new int[this.WindowSize];
        messageTracker = new String[this.WindowSize];
        timerWindowA = new double[this.WindowSize];

        for (int i = 0; i < this.WindowSize; i++) {
            messageTracker[i] = "";
            windowA[i] = i;
            timerWindowA[i] = 0.0;
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
        System.out.println("messageTrackerB START: " + Arrays.toString(messageTrackerB));

        // Check the checksum
        int check = checkSum(packet.getSeqnum(), packet.getAcknum(), packet.getPayload());
        if (check != packet.getChecksum()) {
            // Drop the packet
            System.out.println("");
            System.out.println("CORRUPT PACKET IN B, DROPPING");
            corrupedPackets++;
            return;
        }

        // Add to tracker
        int trackerIndex = seqNumToIndex(windowB, packet.getSeqnum());
        // If out of bounds, we have already acked so re send it
        if (trackerIndex == -1 || (currentB != "" && packet.getPayload().charAt(0) < currentB.charAt(0))) {
            System.out.println("Retransmit... Packet has been received already");
            Packet retransmit = new Packet(0, packet.getSeqnum(), packet.getSeqnum());
            toLayer3(1, retransmit);
            return;
        }

        windowTrackerB[trackerIndex] = 1;
        messageTrackerB[trackerIndex] = packet.getPayload();

        // Check if we need to shift the window at all
        int shift = numOfShift(windowTrackerB);

        if (shift > 0) {
            int[] newWindowB = windowB;
            int[] newWindowTrackerB = windowTrackerB;
            String[] newMessageTrackerB = messageTrackerB;
            for (int i = 0; i < shift; i++) {
                // Send ack back
                Packet ackPacket = new Packet(0, newWindowB[0], newWindowB[0]);
                toLayer3(1, ackPacket);
                numAckPackets++;

                // Send to layer 5
                toLayer5(newMessageTrackerB[0]);
                numLayer5++;

                // Set new vars

                currentB = newMessageTrackerB[0];
                if (currentB.charAt(0) == 'z') {
                    currentB = "";
                }
                newWindowB = shiftWindow(newWindowB);
                newWindowTrackerB = shiftTracker(newWindowTrackerB);
                newMessageTrackerB = shiftMessageTracker(messageTrackerB);
            }
            // Update variables
            windowB = newWindowB;
            windowTrackerB = newWindowTrackerB;
            messageTrackerB = newMessageTrackerB;

        }

        System.out.println("-----------------");
        System.out.println("windowB END: " + Arrays.toString(windowB));
        System.out.println("windowTrackerB END: " + Arrays.toString(windowTrackerB));
        System.out.println("messageTrackerB END: " + Arrays.toString(messageTrackerB));
        System.out.println("///////////////////////////////");

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

        messageTrackerB = new String[this.WindowSize];
        for (int x = 0; x < this.WindowSize; x++) {
            messageTrackerB[x] = "";
        }
    }

    // Use to print final statistics
    protected void Simulation_done() {
        double ratioLost = ((double) (numRetransmit - corrupedPackets) / (numSent + numRetransmit + numAckPackets));
        double ratioCorrupted = ((double) corrupedPackets
                / (numSent + numRetransmit + numAckPackets - numRetransmit - corrupedPackets));

        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO
        // NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A: " + numSent);
        System.out.println("Number of retransmissions by A: " + numRetransmit);
        System.out.println("Number of data packets delivered to layer 5 at B: " + numLayer5);
        System.out.println("Number of ACK packets sent by B: " + numAckPackets);
        System.out.println("Number of corrupted packets: " + corrupedPackets);
        System.out.println("Ratio of lost packets: " + ratioLost);
        System.out.println("Ratio of corrupted packets: " + ratioCorrupted);
        System.out.println("Average RTT: " + (sumRTT / numSent));
        System.out.println("Average communication time: " + (sumCommunication / numRetransmit));
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        // System.out.println("Example statistic you want to check e.g. number of ACK
        // packets received by A :" + "<YourVariableHere>");
    }
}
