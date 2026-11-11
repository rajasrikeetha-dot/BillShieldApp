==========================================================
                      BILL SHIELD
   Bill, Warranty and Vehicle Expiry Reminder System
==========================================================

1. PROJECT OVERVIEW
-------------------
Bill Shield helps users keep track of the expiry dates of
bills, warranties and vehicle documents so that nothing
expires unnoticed. The user adds an item once and the
system reminds them before it expires, both on the website
(bell notifications) and by email.

The backend is written in pure Java (JDK only, no external
libraries). The front end uses HTML, CSS and JavaScript.


2. OBJECTIVES
-------------
- Store warranty and document expiry dates in one place.
- Warn the user 30 days, 1 week and 2 days before expiry.
- Send reminder emails even when the website is closed.
- Keep every user's account and data separate and secure.


3. FEATURES
-----------
Account
- Sign up and login
- Passwords stored securely (PBKDF2 with salt, never plain text)
- Real logout (the session is ended on the server)
- Forgot password: a 6-digit code is sent to the user's email
  (valid for 10 minutes, maximum 5 wrong attempts)
- Account Details page: name, email, overview and quick actions

Items
- Categories: Electronics, Appliances, Vehicle, Others
- Vehicles also track: vehicle number, free service date,
  insurance expiry and PUC expiry
- Live preview card while adding an item
- Delete items with a confirmation box
- Each user sees only their own items

Dashboard
- Greeting with the user's name and initials
- Total, Safe, Due Soon and Expired counts
- Status rules: Expired (past date), Due Soon (30 days or
  less), Safe (more than 30 days)
- Search and category filters
- Upcoming reminders list

Notifications
- Bell icon with a count of items due within 30 days
- Three levels: within 30 days, within 1 week, within 2 days
  (and Expired)
- Pop-up message when something is due within 7 days
- Email reminders sent by the Java server at 30 days,
  1 week and 2 days before expiry (each one is sent only once)


4. TECHNOLOGIES USED
--------------------
Back end   : Java (JDK 17 or later), com.sun.net.httpserver
Front end  : HTML5, CSS3, JavaScript
Email      : SMTP with Gmail (SSL port 465, or STARTTLS port 587)
Security   : PBKDF2WithHmacSHA256, SecureRandom session
             tokens, HttpOnly cookies
Storage    : text and JSON files in the data folder, and the
             browser's localStorage


5. FOLDER STRUCTURE
-------------------
BillShieldApp/
  Server.java      Starts the web server on port 8080 and
                   serves the web folder
  AuthApi.java     Sign up, login, logout, current user,
                   forgot password, reset password
  Mailer.java      Sends emails through Gmail SMTP
  Reminders.java   Saves each user's items and sends the
                   reminder emails
  Util.java        Helper functions (JSON, cookies, hashing)
  mail.txt         Gmail address and App Password
                   (NOT included for security, see mail-example.txt)
  mail-example.txt Sample of how mail.txt must look
  data/            users.txt, items_<user>.json, sent.txt
                   (created automatically)
  web/
    login.html     Login, sign up and forgot password
    dashboard.html Main dashboard and notifications
    add.html       Add item page
    account.html   Account details page
    index.html     Home page


6. REQUIREMENTS
---------------
- Windows laptop or PC
- JDK 17 or later installed (check with: java -version)
- Google Chrome
- Internet connection (only needed for sending emails)
- A Gmail account with 2-Step Verification and an App Password


7. SETUP
--------
Step 1. Copy the BillShieldApp folder to:  C:\BillShieldApp
        (the program expects this location for the data folder)

Step 2. Create the file mail.txt inside C:\BillShieldApp
        with exactly two lines:
            line 1: your Gmail address
            line 2: your Gmail App Password
        Never share this file with anyone.

        How to get an App Password:
        Google Account > Security > 2-Step Verification >
        App passwords > create one named "Bill Shield".


8. HOW TO RUN
-------------
1. Open a terminal in the BillShieldApp folder:
       cd C:\BillShieldApp
2. Compile:
       javac *.java
3. Start the server:
       java Server
4. You will see: "Bill Shield server started."
5. Open Chrome and go to:
       http://localhost:8080
6. To stop the server press Ctrl + C.

Always open the site through http://localhost:8080
(not by double-clicking the html files).


9. HOW TO USE
-------------
1. Create an account on the sign up tab, then log in.
2. Click Add Item, choose a category and fill in the dates.
3. For a vehicle, also fill in the vehicle number, free
   service date, insurance expiry and PUC expiry.
4. The dashboard shows your items with colours:
   green = safe, orange = due soon, red = expired.
5. Click the bell icon to see the reminders.
6. Click Account to see your details.
7. Click Logout to leave safely.
8. Forgot password: click "Forgot password", enter your
   email, type the 6-digit code from the email, then choose
   a new password.


10. HOW THE EMAIL REMINDERS WORK
--------------------------------
- Whenever the dashboard opens or items change, the website
  sends the user's items to the server (/api/sync).
- The server checks all users once every hour.
- If an item's date is 30, 7 or 2 days away (or sooner), an
  email is sent to that user.
- Each reminder is recorded in data/sent.txt so the same
  reminder is never sent twice.
- If the first email route (port 465) is blocked by the
  network, the program tries the second route (port 587).


11. SECURITY
------------
- Passwords are never stored as plain text. Each password
  has a random salt and is hashed with PBKDF2 (120,000
  iterations).
- Sessions use random tokens in HttpOnly cookies.
- Reset codes expire after 10 minutes and allow only 5
  wrong tries.
- The Gmail App Password stays only in mail.txt.


12. API ENDPOINTS
-----------------
POST /api/signup   create an account
POST /api/login    log in
POST /api/logout   log out
GET  /api/me       current user
POST /api/forgot   send reset code to email
POST /api/reset    set a new password with the code
POST /api/sync     save the user's items for reminders
GET  /api/ping     check that the server is running


13. TROUBLESHOOTING
-------------------
- "Could not find or load main class Server"
      Run javac *.java first, inside C:\BillShieldApp.
- Page not found
      Open http://localhost:8080 and keep the server running.
- Email not received
      Check the Spam folder, check mail.txt, and make sure
      the internet allows Gmail. The terminal shows the reason.
- "No account found" after restart
      Check that data/users.txt exists in C:\BillShieldApp.


14. LIMITATIONS
---------------
- Items are kept in the browser, so they are available on the
  same browser only.
- Reminder emails are sent only while the Java server runs.


15. FUTURE SCOPE
----------------
- Store items in a database on the server for every device
- Mobile app with push notifications
- SMS and WhatsApp reminders
- Upload and keep scanned bills and warranty cards
- Run the server on the cloud so reminders work all the time


Developed by: Rajasri
Department of CSE (AI and ML)