# from collections import defaultdict
import re
import bisect

File = []
Results = []

with open('Flows_NORMAL_RUNNING.txt') as f:
    for line in f:
        File.append(line[:-1])

currentSwitch = -1
for i in File:
	if i[0:10] == "2017-10-21":
		Results.append([i,{}])
	elif i[0:8] == "Switch: ":
		if len(i) == 9:
			currentSwitch = i[8]
		else:
			currentSwitch = i[8:10]
	elif i[1:11] == "cookie=0x0":
		if re.findall(r'priority=[0-9]*', i)[0] == "priority=50000":
			src = re.findall(r'nw_src=[0-9.]*', i)[0][7:]
			dst = re.findall(r'nw_dst=[0-9.]*', i)[0][7:]
			port = re.findall(r'output:[0-9]', i)[0][7:]

			if currentSwitch in Results[-1][1]:
				bisect.insort(Results[-1][1][currentSwitch], [src,dst,port])
			else:
				Results[-1][1][currentSwitch] = [[src,dst,port]]


for i in xrange(len(Results) - 1):
	if Results[i][1] != {} and Results[i][1] == Results[i+1][1]:
		print "Experiment Started At: " + str(Results[0][0])
		print "Consensus Reached At: " + str(Results[i][0])
		print "Iterations: " + str(i)
		break