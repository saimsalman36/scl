echo "" > $1

while :
do

	DATE=`date '+%Y-%m-%d %H:%M:%S:%N'`
	echo $DATE >> $1
	echo "Switch: 0" >> $1
	ovs-ofctl dump-flows s000 >> $1
	echo "Switch: 1" >> $1
	ovs-ofctl dump-flows s001 >> $1
	echo "Switch: 2" >> $1
	ovs-ofctl dump-flows s002 >> $1
	echo "Switch: 3" >> $1
	ovs-ofctl dump-flows s003 >> $1
	echo "Switch: 4" >> $1
	ovs-ofctl dump-flows s004 >> $1
	echo "Switch: 5" >> $1
	ovs-ofctl dump-flows s005 >> $1
	echo "Switch: 6" >> $1
	ovs-ofctl dump-flows s006 >> $1
	echo "Switch: 7" >> $1
	ovs-ofctl dump-flows s007 >> $1
	echo "Switch: 8" >> $1
	ovs-ofctl dump-flows s008 >> $1
	echo "Switch: 9" >> $1
	ovs-ofctl dump-flows s009 >> $1
	echo "Switch: 10" >> $1
	ovs-ofctl dump-flows s010 >> $1
	echo "Switch: 11" >> $1
	ovs-ofctl dump-flows s011 >> $1
	echo "Switch: 12" >> $1
	ovs-ofctl dump-flows s012 >> $1
	echo "Switch: 13" >> $1
	ovs-ofctl dump-flows s013 >> $1
	echo "Switch: 14" >> $1
	ovs-ofctl dump-flows s014 >> $1
	echo "Switch: 15" >> $1
	ovs-ofctl dump-flows s015 >> $1
	echo "Switch: 16" >> $1
	ovs-ofctl dump-flows s016 >> $1
	echo "Switch: 17" >> $1
	ovs-ofctl dump-flows s017 >> $1
	echo "Switch: 18" >> $1
	ovs-ofctl dump-flows s018 >> $1
	echo "Switch: 19" >> $1
	ovs-ofctl dump-flows s019 >> $1
	echo "" >> $1

	echo "Running ..."
	sleep 1
done