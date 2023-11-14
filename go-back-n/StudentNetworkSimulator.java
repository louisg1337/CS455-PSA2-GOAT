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

    // Window buffer for tracking sent packets
    Packet[] sentBuffer;

    // Double list for storing communication times
    LinkedList<Double> roundTripTimes = new LinkedList<Double>();
    // Double array for tracking sent and acknowledgment times
    double[] timeSent;
    double[] timeAcknowledged;
    // Int variable for tracking the next sequence number A will send to B
    private int nextSequenceNumber = FirstSeqNo;
    // Int variable for keeping track of the base of the sending window
    private int base = 1;
    // Int variable for tracking the next sequence number B expects to receive
    private int expectedSequenceNumber = 1;
    // Initialize a new packet for impending acknowledgment transmission later
    Packet acknowledgmentPacket = new Packet(0, 0, 0);
    // Int variable for tracking the number of packets sent
    private int packetsSent = 0; 
    // Int variable tracking the number of retransmissions
    private int retransmissionCount = 0;
    // Int variable tracking the number of packets received 
    private int receivedCount = 0;
    // Int variable for the number of packets sent to layer 5
    private int packetsToLayer5 = 0;
    // Int variable tracking the number of lost packets
    private int lostPacketsCount = 0;
    // Int variable tracking the number of corrupted packets received
    private int corruptedPacketsCount = 0;
    // Int variable for the number of ACKs sent by B
    private int acknowledgmentsSent = 0;
    // Int variable for the number of ACKs received by A
    private int acknowledgmentsReceived = 0;
   
    // Translate String 'data' into an int, and add this to the sequence and acknowledgment to generate a checksum
    // Then return that checksum
    protected int calculateChecksum(int sequenceNumber, int acknowledgmentNumber, String data) {
        // Generate int representation of our payload
        int payloadHashCode = data.hashCode();
        // Return sequence + acknowledgment + int(payload)
        return sequenceNumber + acknowledgmentNumber + payloadHashCode;
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
        LimitSeqNo = winsize + 1; // set appropriately; set for GBN
        RxmtInterval = delay;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send. It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        // Store data from received message in String variable for use in payload later
        String data = message.getData();
        if (nextSequenceNumber < base + WindowSize) {
            // Generate checksum for impending packet creation
            int checksum = calculateChecksum(nextSequenceNumber, -1, data);
            // Create a new packet with the generated checksum
            Packet packet = new Packet(nextSequenceNumber, -1, checksum, data);
            // Print String representation of the packet
            System.out.println("aOutput received: " + packet.toString());
            // If we reached the end of the array...
            if (nextSequenceNumber > WindowSize) {
                System.out.println("Wrapping Around");
                // Wrap around
                sentBuffer[nextSequenceNumber % LimitSeqNo] = packet;
            } else {
                // Otherwise, just add the new packet to the sending buffer (will be removed once acknowledgment received)        
                sentBuffer[nextSequenceNumber] = packet;
            }
            // Send the packet to layer 3 of B
            toLayer3(A, packet);
            // Record start time and add to the list
            long startTime = System.nanoTime();
            System.out.println("Time sent: " + startTime);
            timeSent[nextSequenceNumber] = startTime;
            // Increment the packetsSent counter
            packetsSent++;
            System.out.println("aOutput sent: " + packet.toString());
            // If the base of the window gets slid over but nextSequenceNumber is not updated...
            if (base == nextSequenceNumber) {
                // Start a timer for A for the time specified by the delay inputted by the user
                // Triggering a timeout in which lost packets are retransmitted
                startTimer(A, RxmtInterval);
            }
            // Increment nextSequenceNumber
            nextSequenceNumber++;
        } else {
            return;
        }
    }

    protected void aInput(Packet receivedPacket) {
        // Record acknowledgment time and add to the list
        double acknowledgmentTime = System.nanoTime();
        System.out.println("Acknowledgment time: " + acknowledgmentTime);
        timeAcknowledged[nextSequenceNumber] = acknowledgmentTime;
        // Make a copy of the received packet and store it as Packet 'packet'
        Packet packet = new Packet(receivedPacket);
        // Print String representation of Packet 'packet'
        System.out.println("aInput received: " + packet.toString());
        // Store fields of 'packet'
        int sequenceNumber = packet.getSeqnum();
        int acknowledgmentNumber = packet.getAcknum();
        int checksum = packet.getChecksum();
        // Calculate what checksum should be and store it in the variable 'expectedChecksum'
        int expectedChecksum = sequenceNumber + acknowledgmentNumber;
        // Compare this calculated checksum with that of the packet received
        // If no corruption...
        if (checksum == expectedChecksum) {
            // Update the base
            base = packet.getAcknum() + 1;
            // If we have slid the base of our window over to the next sequence number...
            if (base == nextSequenceNumber) {
                // Stop the timer because we are where we want to be
                stopTimer(A);
                acknowledgmentsReceived++;
            } else {
                // Otherwise, trigger a timeout in which we will retransmit not acknowledged packets
                startTimer(A, RxmtInterval);
            }
        } else {
            // Increment corrupted packets counter
            corruptedPacketsCount++;
        }
    }

    // This routine will be called when A's timer expires.
    protected void aTimerInterrupt() {
        // Start a timer for retransmission
        startTimer(A, RxmtInterval);
        // Resend all packets previously sent but not yet acknowledged (waiting in send buffer)
        for (int i = base; i < nextSequenceNumber; i++) {
            // If we are past the window...
            if (i > WindowSize) {
                // Print String representation of the packet 
                System.out.println("aTimerInterrupt (Wrap): " + sentBuffer[i % LimitSeqNo].toString());
                // Print sequence number
                System.out.println("nextSequenceNumber = " + nextSequenceNumber);
                // Send the packet to layer 3
                toLayer3(A, sentBuffer[i % LimitSeqNo]);
                // Increment packetsSent and retransmission counters
                packetsSent++;
                retransmissionCount++;
            } else {
                // Print String representation of the packet
                System.out.println("aTimerInterrupt: " + sentBuffer[i].toString());
                // Send the packet to layer 3
                toLayer3(A, sentBuffer[i]);
                // Increment packetsSent and retransmission counters
                packetsSent++;
                retransmissionCount++;
            }
        }
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g., of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        // Initialize the window buffer to windowSize + 1, to allow for wrap around
        sentBuffer = new Packet[LimitSeqNo];
        timeSent = new double[1050];
        timeAcknowledged = new double[1050];
    }

    // This routine will be called whenever a packet numSent from the A-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side. "packet" is the (possibly corrupted) packet
    // numSent from the A-side.
    protected void bInput(Packet receivedPacket) {
        // Increment the received counter
        receivedCount++;
        // Make a copy of the received packet and store it as Packet 'packet'
        Packet packet = new Packet(receivedPacket);
        // Print String representation of Packet 'packet'
        System.out.println("bInput received: " + packet.toString());
        // Print sequence number
        System.out.println("nextSequenceNumber = " + nextSequenceNumber);
        // Print expected sequence number
        System.out.println("expectedSequenceNumber = " + expectedSequenceNumber);
        // Store fields of 'packet'
        int sequenceNumber = packet.getSeqnum();
        int acknowledgmentNumber = packet.getAcknum();
        int checksum = packet.getChecksum();
        String payload = packet.getPayload();
        // Calculate what checksum should be and store it in the variable 'expectedChecksum'
        int expectedChecksum = calculateChecksum(sequenceNumber, acknowledgmentNumber, payload);
        // Print this calculated checksum
        System.out.println("Checksum should be: " + expectedChecksum);
        // Declare variable for storing acknowledgment checksum
        int acknowledgmentChecksum;
        // Compare calculated checksum with that of the packet received, as well as the sequence number
        // If the sequence number and checksum are what we expect...
        if ((sequenceNumber == expectedSequenceNumber) && (checksum == expectedChecksum)) {
            // Send the payload to layer 5
            toLayer5(payload);
            // Increment the packetsToLayer5 counter
            packetsToLayer5++;
            // Generate acknowledgment number for acknowledgment packet
            acknowledgmentNumber = sequenceNumber;
            // Generate new checksum for acknowledgment packet
            acknowledgmentChecksum = sequenceNumber + acknowledgmentNumber;
            // Update the acknowledgment packet
            acknowledgmentPacket = new Packet(expectedSequenceNumber, acknowledgmentNumber, acknowledgmentChecksum);
            // Print String representation of the acknowledgment packet
            System.out.println("New acknowledgmentPacket: " + acknowledgmentPacket.toString());
            // Send acknowledgment to layer 3
            toLayer3(B, acknowledgmentPacket);
            // Increment the acknowledgmentsSent counter and update the expected sequence number
            acknowledgmentsSent++;
            expectedSequenceNumber++;
            System.out.println("New expectedSequenceNumber: " + expectedSequenceNumber);
        } else {
            if (checksum != expectedChecksum) {
                // Increment corrupted packets counter
                corruptedPacketsCount++;
            }
            // If no last packet received (first message lost), return
            if (acknowledgmentPacket.getSeqnum() <= 0) {
                return;
            } else {
                // Otherwise, print String representation of the last acknowledgment packet and send it to layer 3
                System.out.println("Last acknowledgmentPacket: " + acknowledgmentPacket.toString());
                toLayer3(B, acknowledgmentPacket);
            }
        }
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {

    }

    // Use to print final statistics
    protected void Simulation_done() {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIABLE NAMES. DO
        // NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        //RTT CALCULATION
        lostPacketsCount = packetsSent - receivedCount;
        double sumRTT = 0;
        double averageRTT;

        //add total RTT measurements together
        for (int i = 0; i < timeSent.length - 1; i++){
            roundTripTimes.add(i, timeAcknowledged[i] - timeSent[i]);

            //sum up the RTTs
            sumRTT += roundTripTimes.get(i);
        }
        //divide by number of measurements to get the average rtt
        averageRTT = sumRTT/roundTripTimes.size();

        //calcualting the ratios
        int totalPackets = packetsSent + retransmissionCount;
        int totalNotLost = totalPackets - lostPacketsCount;
        int totalNotCorrupted = totalPackets - corruptedPacketsCount;

        double ratioLost = (lostPacketsCount / totalNotCorrupted);
        double ratioCorrupted = (corruptedPacketsCount / totalNotLost);



        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + packetsSent);
        System.out.println("Number of retransmissions by A:" + retransmissionCount);
        System.out.println("Number of data packets delivered to layer 5 at B:" + packetsToLayer5);
        System.out.println("Number of ACK packets sent by B:" + acknowledgmentsSent);
        System.out.println("Number of corrupted packets:" + corruptedPacketsCount);
        System.out.println("Ratio of lost packets:" + ratioLost);
        System.out.println("Ratio of corrupted packets:" + ratioCorrupted);
        System.out.println("Average RTT:" + averageRTT);
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        // System.out.println("Example statistic you want to check e.g. number of ACK
        System.out.println("Number of ACK packets received by A:" + acknowledgmentsReceived);
    }

}