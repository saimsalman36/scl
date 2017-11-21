echo "" > $1
numSwitches=$2

while :
do
	# STARTTIME=$(($(date +%s%N)/1000000))
	DATE=`date '+%Y-%m-%d %H:%M:%S:%N'`
	echo $DATE >> $1

	for (( i=0; i<$numSwitches; i++))
		do
			echo "Switch: $i" >> $1
			ovs-ofctl dump-flows `printf "s%03d" $i` >> $1
		done
	# ENDTIME=$(($(date +%s%N)/1000000))
	# echo "It takes $((ENDTIME - STARTTIME)) milliseconds to complete this task..." >> $1
	sleep 0.5
done