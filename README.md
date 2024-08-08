# Assignment 2

## Compilation

The project is implemented in Java SE 21.

When using the Intellij terminal for compilation, it is necessary to enter the second src file inside the g17 one. 

Example: 
```
cd C:\Users\Utilizador\Desktop\CPD\g17\assign2\src\src
```

Then, use the command `javac *.java` to compile all the java files.

## Run the server

To run the server, use the command `java Server`. After following the instructions, the server will be waiting for the clients to join.

Notes: 
- The lobby size is the number of players that are going to play in the same game!
- The Maximum Number of Lobbies is the number of game instances that can be played at the same time!

## Run the Clients

To run the clients, use the command `java Player` and then authenticate the player.

## Game rules 

The objective of the game is to guess the 5 letter word.

Each player has 6 attempts and the winner is the player that guesses first. If they guess it in the same round it's a tie.

When you input a word:
- if the letters turn green, it means that the letter's position matches the position in the word
- if the letters turn yellow, it means that the word contains the letter but in a different position
- if the color of the letters doesn't change, it means that the word does not contain that letter



