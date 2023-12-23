
$('#myTabs a').click(function (e) {
    e.preventDefault()
    $(this).tab('show')
});

$(document).ready(function () {
    Highcharts.setOptions({
        colors: ['#8129dd', '#8ec63f', '#2756ca', '#f1b601', '#f86423', '#27aae3']
    });
    // 用户评分级别占比
    var chart1 = $("#container1").highcharts({
        chart: {
            renderTo: 'container1', //装载图表的div容器id
            type: 'pie',
            backgroundColor: '#1e2131',
            plotBorderColor: '#1c2a38',
            plotBorderWidth: 1,
        },
        title: false,
        subtitle: false,
        exporting: {
            enabled: false,
        },
        legend: {
            enabled: true, // 启用图例
            layout: 'horizontal',
            align: 'center',
            verticalAlign: 'bottom',
            itemStyle: { cursor: 'pointer', color: '#FFF' },
            itemHiddenStyle: { color: '#CCC' },
        },
        plotOptions: {
            pie: {
                innerSize: '70%', // 设置内环的大小，创建环形图
                borderRadius: '10px', // 设置圆角
                dataLabels: {
                    enabled: true,
                    color: '#fff',
                },
            },
        },
        series: [{
            name: '评分',
            data: [
                { name: '1分', y: 8759 },
                { name: '2分', y: 24464 },
                { name: '3分', y: 35089 },
                { name: '4分', y: 23120 },
                { name: '5分', y: 12217 },
            ],
            dataLabels: {
                enabled: true,
                format: '{point.name}: <b>{point.percentage:.1f}%</b>'
            },
            colors: ['#8129dd', '#8ec63f', '#2756ca', '#f1b601', '#f86423'],
        }],
    });

    // 电影推荐系统数据分析平台
    var chart2 = $("#jglxchart").highcharts({
        chart: {
            type: 'bar',
            backgroundColor: '#1e2131',
            plotBorderColor: '#1c2a38',
            plotBorderWidth: 1,
        },
        title: false,
        subtitle: false,
        exporting: {
            enabled: false,
        },
        xAxis: {
            categories: ['流浪地球', '哪吒之魔童降世', '少年的你', '你好，李焕英', '寄生虫 기생충', '复仇者联盟4', '唐人街探案3', '满江红', '封神第一部：朝歌风云', '小丑 Joker'],
            labels: {
                style: {
                    color: '#9ea0ae',
                },
            },
            tickWidth: 0,
            tickColor: '#1c2a38',
            lineColor: '#1c2a38',
        },
        yAxis: {
            title: {
                text: '评分人数',
                align: 'high',
                y: 5,
                x: -130,
            },
            tickColor: '#1c2a38',
            gridLineColor: '#1c2a38',
            labels: {
                overflow: 'justify',
            },
        },
        tooltip: {
            valueSuffix: ' 个',
        },
        plotOptions: {
            line: {
                dataLabels: {
                    enabled: true,
                    allowOverlap: true,
                    color: '#fff',
                },
                color: '#f1b601',
            },
        },
        legend: false,
        credits: {
            enabled: false,
        },
        series: [{
            name: '历史热门电影',
            data: [1937940, 1835122, 1476365, 1425815, 1389846, 1078100, 1065507, 1040562, 1026632, 1020824],
            color: '#f1b601',
            marker: {
                symbol: 'circle',
            },
            dataLabels: {
                enabled: true
            }
        }],
    });

    // 历史热门电影Top10
    var chart3 = $("#qst-monthchart").highcharts({
        chart: {
            backgroundColor: '#1e2131',
            plotBorderColor: '#1c2a38',
            plotBorderWidth: 1
        },
        title: false,
        credits: {
            enabled: false // 禁用版权信息
        },
        legend: {
            enabled: true, // 启用图例
        },
        xAxis: {
            categories: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'],
            tickColor: '#1c2a38',
            gridLineColor: '#1c2a38',
            lineColor: '#1c2a38',
        },
        yAxis: {
            title: false,
            gridLineColor: '#1c2a38',
            tickColor: '#1c2a38'
        },
        series: [
            {
                name: '2019',
                data: [15, 18, 16, 13, 26, 17, 12, 21, 35, 17, 23, 30],
                dataLabels: {
                    enabled: true
                }
            }, {
                name: '2020',
                data: [25, 27, 7, 5, 9, 11, 25, 20, 41, 29, 28, 35],
                dataLabels: {
                    enabled: true
                }
            }, {
                name: '2021',
                data: [22, 15, 14, 29, 28, 24, 31, 21, 25, 30, 30, 22],
                dataLabels: {
                    enabled: true
                }
            }, {
                name: '2022',
                data: [28, 27, 16, 23, 40, 21, 30, 27, 52, 21, 21, 22],
                dataLabels: {
                    enabled: true
                }
            }, {
                name: '2023',
                data: [18, 18, 39, 29, 20, 17, 18, 23, 30, 11, 5, 9],
                dataLabels: {
                    enabled: true
                }
            }
        ]
    });

    // 电影上映数量时间分布
    var chart4 = $("#rj-daychart").highcharts({
        chart: {
            type: 'pie',
            backgroundColor: '#1e2131',
            plotBorderColor: '#1c2a38',
            plotBorderWidth: 1,
        },
        title: false,
        subtitle: false,
        exporting: {
            enabled: false,
        },
        legend: {
            enabled: true, // 启用图例
            left: 'center',
            layout: 'horizontal',
            align: 'center',
            verticalAlign: 'bottom',
            itemStyle: { cursor: 'pointer', color: '#FFF' },
            itemHiddenStyle: { color: '#CCC' },
        },
        plotOptions: {
            pie: {
                innerSize: '70%', // 设置内环的大小，创建环形图
                borderRadius: '10px', // 设置圆角
                dataLabels: {
                    enabled: true,
                    color: '#fff',
                },
            },
        },
        series: [{
            name: '评分',
            data: [
                { name: '1分', y: 8759 },
                { name: '2分', y: 24464 },
                { name: '3分', y: 35089 },
                { name: '4分', y: 23120 },
                { name: '5分', y: 12217 },
            ],
            dataLabels: {
                enabled: true,
                format: '{point.name}: <b>{point.percentage:.1f}%</b>'
            },
            colors: ['#8129dd', '#8ec63f', '#2756ca', '#f1b601', '#f86423'],
        }],
    });

    // 用户喜好电影类型占比
    var chart5 = $("#fbt-monthchart").highcharts({
        chart: {
            backgroundColor: '#1e2131',
            plotBackgroundColor: null,
            plotBorderWidth: null,
            plotShadow: false
        },
        title: false,
        tooltip: {
            headerFormat: '{series.name}<br>',
            pointFormat: '{point.name}: <b>{point.percentage:.1f}%</b>'
        },
        exporting: {
            enabled: false, //用来设置是否显示‘打印’,'导出'等功能按钮，不设置时默认为显示
        },
        credits: {
            enabled: false // 禁用版权信息
        },
        legend: {
            layout: 'horizontal',
            align: 'center',
            verticalAlign: 'bottom',
            itemStyle: { cursor: 'pointer', color: '#FFF' },
            itemHiddenStyle: { color: '#CCC' },
        },
        plotOptions: {
            pie: {
                allowPointSelect: true,
                cursor: 'pointer',
                dataLabels: {
                    enabled: true,
                    format: '<b>{point.name}</b>: {point.percentage:.1f} %',
                    style: {
                        color: (Highcharts.theme && Highcharts.theme.contrastTextColor) || '#FFF'
                    }
                }
            }
        },
        series: [{
            type: 'pie',
            name: '电影类型占比',
            data: [
                ['剧情', 53655],
                ['喜剧', 22997],
                ['动画', 16860],
                ['动作', 16008],
                ['爱情', 14539],
                ['冒险', 12489],
                ['犯罪', 12489],
                ['惊悚', 11674],
                ['奇幻', 11063],
                ['悬疑', 8140],
                ['科幻', 7654],
                ['纪录片', 6521],
                ['恐怖', 4831],
                ['家庭', 5017],
                ['战争', 3858],
                ['专记', 3815],
                ['历史', 3321],
                ['短片', 2815],
                ['运动', 2551],
                ['音乐', 2171],
                ['歌舞', 1938],
                ['灾难', 952],
                ['真人秀', 811],
                ['儿童', 778],
                ['古装', 644],
                ['武侠', 379],
                ['西部', 198],
                ['脱口秀', 184],
                ['戏曲', 69]
            ]
        }]
    });

    // 最近热门电影Top10
    var chart6 = $("#zxlxchart").highcharts({
        chart: {
            type: 'bar',
            backgroundColor: '#1e2131',
            plotBorderColor: '#1c2a38',
            plotBorderWidth: 1,
        },
        title: false,
        subtitle: false,
        exporting: {
            enabled: false,
        },
        xAxis: {
            categories: ['奥本海默 Oppenheimer', '坚如磐石', '河边的错误', '93国际列车大劫案：莫斯科行动', '第八个嫌疑人', '志愿军：雄兵出击',
                '前任4：英年早婚', '拯救嫌疑人', '威尼斯惊魂夜 A Haunting in Venice', '电锯惊魂10 Saw X'],
            labels: {
                style: {
                    color: '#9ea0ae',
                },
            },
            tickWidth: 0,
            tickColor: '#1c2a38',
            lineColor: '#1c2a38',
        },
        yAxis: {
            title: {
                text: '评论人数',
                align: 'high',
                y: 5,
                x: -130,
            },
            tickColor: '#1c2a38',
            gridLineColor: '#1c2a38',
            labels: {
                overflow: 'justify',
            },
        },
        tooltip: {
            valueSuffix: ' 个',
        },
        plotOptions: {
            line: {
                dataLabels: {
                    enabled: true,
                    allowOverlap: true,
                    color: '#fff',
                },
                color: '#8ec63f',
            },
        },
        legend: true,
        credits: {
            enabled: false,
        },
        series: [{
            name: '最近热门',
            data: [559843, 215189, 205244, 116943, 108260, 86806, 70117, 45248, 39903, 39444],
            color: '#8ec63f',
            marker: {
                symbol: 'circle',
            },
            dataLabels: {
                enabled: true
            }
        }],
    });
});