"""Compile the actual data model and exercise gaps, quotes and bounded rendering inputs."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as directory:
    source = Path(directory, 'ChartTest.java')
    source.write_text(r'''import org.telegram.ui.web.AmpChartData;
public class ChartTest {
 static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("accepted invalid chart");}
 public static void main(String[] args){
  AmpChartData line=new AmpChartData(AmpChartData.Kind.LINE,"test",AmpChartData.csv("date\tA\tB\n2020\t1\t\n2021\t\t3\n"));
  if(line.values.length!=2||line.series.length!=2||!Double.isNaN(line.values[0][1])||line.values[1][1]!=3)throw new AssertionError("gaps");
  AmpChartData dates=new AmpChartData(AmpChartData.Kind.LINE,"dates",AmpChartData.csv("date,value\n2020-01-01,1\n2020-01-02,2\n2020-02-01,3"));
  if(Math.abs(dates.positions[1]-1.0/31)>1e-9)throw new AssertionError("Unequal dates need proportional spacing");
  AmpChartData pie=new AmpChartData(AmpChartData.Kind.PIE,"test",AmpChartData.csv("label,value\r\n\"North, west\",25\r\nSouth,75\r\n"));
  if(pie.total()!=100||!pie.labels[0].equals("North, west"))throw new AssertionError("pie or CSV quotes");
  AmpChartData bar=new AmpChartData(AmpChartData.Kind.BAR,"test",AmpChartData.csv("label,value\nA,-3\nB,4"));
  if(bar.values[0][0]!=-3)throw new AssertionError("negative bar");
  reject(()->AmpChartData.csv("x,y\n\"unclosed,1"));
  reject(()->AmpChartData.csv("x,y\n\"closed\"bad,1"));
  reject(()->new AmpChartData(AmpChartData.Kind.LINE,"",AmpChartData.csv("x,y\nA,Infinity")));
  reject(()->new AmpChartData(AmpChartData.Kind.LINE,"",AmpChartData.csv("x,y\nA,NaN")));
  reject(()->new AmpChartData(AmpChartData.Kind.LINE,"",AmpChartData.csv("x,y\nA,1e99")));
  reject(()->new AmpChartData(AmpChartData.Kind.PIE,"",AmpChartData.csv("x,y\nA,-1")));
  reject(()->new AmpChartData(AmpChartData.Kind.PIE,"",AmpChartData.csv("x,y\nA,0")));
  AmpChartData grouped=new AmpChartData(AmpChartData.Kind.BAR,"",AmpChartData.csv("x,y,z\nA,1,2"));
  if(grouped.series.length!=2)throw new AssertionError("grouped bars");
  reject(()->new AmpChartData(AmpChartData.Kind.STACKED,"",AmpChartData.csv("x,y,z\nA,1,-2")));
  AmpChartData weighted=new AmpChartData(AmpChartData.Kind.MARIMEKKO,"",AmpChartData.csv("x,y\nA,2\nB,4")).weighted(new double[]{1,3},"weight");
  if(weighted.widths[1]!=3)throw new AssertionError("weights");
  reject(()->weighted.weighted(new double[]{0,3},"weight"));
  reject(()->weighted.weighted(new double[]{Double.NaN,3},"weight"));
  reject(()->new AmpChartData(AmpChartData.Kind.LINE,"",AmpChartData.csv("x,y\nA")));
  StringBuilder huge=new StringBuilder("x,y\n");for(int i=0;i<1501;i++)huge.append("A,1\n");reject(()->AmpChartData.csv(huge.toString()));
  System.out.println("Native charts: 18 model and input boundary cases passed.");
 }
}''', encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, str(root / 'TMessagesProj/src/main/java/org/telegram/ui/web/AmpChartData.java'), str(source)], check=True)
    subprocess.run(['java', '-cp', directory, 'ChartTest'], check=True)
