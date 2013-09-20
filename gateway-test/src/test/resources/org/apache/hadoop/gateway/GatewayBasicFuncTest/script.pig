A = load '/tmp/GatewayWebHCatFuncText/pig/passwd.txt' using PigStorage(':');
B = foreach A generate $0 as id;
dump B;