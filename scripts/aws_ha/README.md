# Steps 1

For master and backup

Setup AWS/HA config in setup.sh

Run setup.sh

# Steps 2

Deploy dex service on master

Run dex service on master (can be ignored, because the keepalived will run start_server.sh)

Run 'sudo systemctl start keepalived' on master

Wait for a while

Run 'sudo systemctl start keepalived' on backup
