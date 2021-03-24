#!/usr/bin/env bash

export DEX_DIR=$(cd `dirname "${BASH_SOURCE[0]}"`/../.. && pwd)
envFile="$DEX_DIR/scripts/env.sh"
echo "Env file $envFile"

function createEnv(){
    #Remember to adjust checkAllSet function if you add/remove line(s)
    echo '#!/usr/bin/env bash'  >> ${envFile}

    echo "# Use command below to migrate env file, step: "  >> ${envFile}
    echo "# 1 backup as 0env.sh;"  >> ${envFile}
    echo "# 2 generate new file;"  >> ${envFile}
    echo "# 3 execute command below. use 'sed -i .bak' under MacOS."  >> ${envFile}
    #sed 's/\//\\\//g' 0env.sh | grep -E '^export' | awk -F= '{print "sed -i '\''s/^"$1".*/"$0"/'\'' env.sh"}' | sh
    echo "#sed 's/\//\\\\\\//g' 0env.sh | grep -E '^export' | awk -F= '{print \"sed -i '\''s/^\"\$1\".*/\"\$0\"/'\'' env.sh\"}' | sh"  >> ${envFile}

    echo '' >> ${envFile}
    echo 'export SERVER_PORT="8080"' >> ${envFile}
    echo '# profile: prod | stage | test' >> ${envFile}
    echo 'export DEPLOY_ENV="test"' >> ${envFile}
    echo '# SKIP_GIT=1 makes skipping git operation in stop_and_sync_code.sh' >> ${envFile}
    echo '#export SKIP_GIT=1' >> ${envFile}
    echo '#export SKIP_NPM_INSTALL=1' >> ${envFile}
    echo '# SKIP_BASH_WORK=1 makes skipping lots of work' >> ${envFile}
    echo '#export SKIP_BASH_WORK=1' >> ${envFile}
    
    echo '' >> ${envFile}
    echo '# Database configurations' >> ${envFile}
    echo 'export DEX_MYSQL_HOST="localhost"' >> ${envFile}
    echo 'export DEX_MYSQL_PORT=3306' >> ${envFile}
    echo 'export DEX_MYSQL_USER=root' >> ${envFile}
    echo 'export MYSQL_PWD="youPassword"' >> ${envFile}
    echo 'export DB_NAME="conflux_dex"' >> ${envFile}
    echo '# Whether drop database before execute init.sql' >> ${envFile}
    echo 'export DROP_DB="false"' >> ${envFile}
    
    echo '' >> ${envFile}
    echo '# Account configurations' >> ${envFile}
    echo 'export UI_ADMIN_ADDRESS="your_address"' >> ${envFile}
    echo 'export DEX_ADMIN_ADDRESS="your_address"' >> ${envFile}
    echo 'export DEX_ADMIN_PRIVATE_KEY="your_key"' >> ${envFile}
    echo 'export BOOMFLOW_ADMIN_ADDRESS="your_address"' >> ${envFile}
    echo 'export BOOMFLOW_ADMIN_PRIVATE_KEY="your_key"' >> ${envFile}

    echo '' >> ${envFile}
    echo '# Blockchain configurations' >> ${envFile}
    echo 'export CFX_URL="https://test.confluxrpc.org/v2"' >> ${envFile}

    echo '' >> ${envFile}
    echo '# Metrics configurations' >> ${envFile}
    echo '# Different env use different influxdb database' >> ${envFile}
    echo '#export INFLUXDB_DATABASE="dex"' >> ${envFile}
    echo '#export INFLUXDB_URL="http://127.0.0.1:8086"' >> ${envFile}

    echo '# Entries will be appended below when deploying.' >> ${envFile}
    #Remember to adjust checkAllSet function if you add/remove line(s)

    echo "Done, file created at $envFile"
}
function checkAllSet(){
    props=('DEX_ADMIN_PRIVATE_KEY' 'DEPLOY_ENV')
    ok=true
    for (( i = 0; i < ${#props[@]}; ++i )); do
        value=$(eval echo '$'${props[i]})
#        echo "${props[i]} is ${value}"
        if [[ -z $value ]]; then
            echo "Environment is empty: ${props[i]}, pleas fix it.";
            ok=false
        fi
    done
    if [[ "$ok" = true ]]; then
        echo "Load env file ok"
    else
        exit 1
    fi
}

if [[ -f $envFile ]]; then
    source $envFile
    checkAllSet
else
    echo "Env file not found, now create."
    createEnv
    echo "Please config env in $envFile"
    exit 1
fi