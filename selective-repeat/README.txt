=========================================
INSTRUCTIONS TO COMPILE selective-repeat
WRITTEN BY MATTHEW BERK AND LOUIS GRASSI
=========================================

Be sure to first navigate into the
directory called "selective-repeat"

Compilation:
If the .class files are not already present, type:
javac Project.java
This should result in the .class files being made

Running it:
type: java Project
This should prompt you to enter a few bits of info
Number of messages: how many messages to simulate
Loss Probability: the probability of losing a packet
Corruption Probability: the probability of a non-lost packet being corrupted
Average Time between messages: how long the sender will wait before generating a message
Window Size: the size of the send window, influences the max sequence number
Retransmission Timeout: How long the timer will wait before a timeout event occurs
Trace Level: amount of information to display, for debugging purposes
Random Seed: random seed to effect how the simulated environment performs

After this, the messages should be passed to and from with final
analytics being displayed to the user.