#!/bin/bash

# Install required applications, libaries and tools for a clean machine.
# Note, this script is for Ubuntu 18.04 only, and do not guarantee other OS types.

set -e

# Install jdk 8 and gradle 6
sudo add-apt-repository ppa:cwchien/gradle
sudo apt update
sudo apt install openjdk-8-jdk -y
sudo apt install gradle -y

# Install nodejs 10.x and npm
curl -sL https://deb.nodesource.com/setup_10.x | sudo -E bash -
sudo apt-get install nodejs -y
sudo apt-get install build-essential -y

# Install mysql & setup root user
sudo apt install mysql-server -y
if [ ! -z $1 ]; then
	sudo mysql -e "update mysql.user set plugin = 'mysql_native_password', authentication_string = PASSWORD('$1') where User='root'; flush privileges"
	sudo service mysql restart
fi

# Install some other tools
sudo apt install curl -y
sudo apt install jq -y
sudo apt-get install ntp -y
sudo apt install zip -y
