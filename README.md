# Voting - Simple Single-Elimination Tournament Manager

Vote on things with your friends and decide a single winner by doing a single elimination tournament. This web application manages creating the tournament, showing the progress, managing user logins, letting users vote and resolving the tournament. Entries in the tournaments are represented by images. These could be anything that you would normally put in a tier list, for example.

In order to keep things private and reduce voter fraud, all users who want to vote need a login which has to be created by an admin user.

This project was created to get some practice with kotlin. It Uses [ktor](https://ktor.io/) as the web server and [Exposed](https://github.com/JetBrains/Exposed) as the database ORM framework.

## Example

Here's an example of what a user would see when voting for the best flag from the 5 flags featured in the 2015 New Zealand referendum. Voting for this tournament is still in progress.

Here, round 1 has reached the voting threshold of 3 votes. This means that it has advanced to round 3 and cannot be voted on anymore. However, rounds 2 and 3 have not yet recieved 3 votes and so you can still change your vote. 

![alt text](https://github.com/Gareth001/voting/blob/master/example.png "Example tournament")

## Features 
- Handle any number of entrants (at least 2), and creates the voting bracket for you
- Auto-resolves any rounds after they have gotten enough votes

## Limitations
- Draws are not allowed, only an odd number of votes can be made in each round
- Entrants cannot be seeded
- No mobile interface

## Setup (Hosting the web server)
1. Database

A database is used to store users and round information. The server will look for an instance of MariaDB on port 3306. 

Redis is also used as a caching server. Ensure you have redis running locally before starting the server.

Note that the image files for the entrants are stored on the local filesystem, so write access is required. This will be a problem if deploying on e.g. Google App Engine.

2. Secrets

The web server will look for the following environment variables on startup.
- HASH_KEY - secret key for hashing and signing sessions
- DATABASE_IP - IP address of database, defaults to `localhost`
- DATABASE_USER - username of database user 
- DATABASE_PASS - password for DATABASE_USER
- ADMIN_PASS - password for the automatically created `admin` user 

3. Running the server

To run locally with gradle, run `gradle run`. This will start the server on port 8080.

To deploy, run `gradle build` to create `voting-1.0.jar` in build/libs. Copy this jar to a new directory. 
Create the directory structure `resources/static/uploaded` and copy `style.css` into `resources/static`. Run the server with `java -jar voting-1.0.jar`.

If you want to use HTTPS, there are many guides to setting up a HTTPS reverse proxy with Nginx.