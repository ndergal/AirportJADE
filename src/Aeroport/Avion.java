package Aeroport;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import Aeroport.TourDeControle.Code;
import Aeroport.TourDeControle.Info;
import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.Location;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.ControllerException;

@SuppressWarnings("serial")
public class Avion extends Agent {




	static class Flight implements java.io.Serializable {
		private String destContainerName;
		private long duration;
		public Flight(String destination, long duration) {
			this.destContainerName = destination;
			this.duration = duration;
		}
	}

	private int speed; // en km/h
	private int height;
	private Flight vol;
	private final Flight monitor = new Flight(null,0);
	private long dureeRestante;
	private Info info; 
	private boolean negociating = false;
	
	//interaction avec l'utilisateur:
	//0 --> demande du temps restant
	//1 --> demande de la distance parcourue
	//2 --> demande de la distance restante
	private String agentName;
	private String startContainerName;
	private String destTourName;
	private String startTourName;
	
	private int distanceTotale;
	private double distanceRestante;
	private double distanceParcourue;
	private int tempsEcoule;

	public void setup() {
		Object[] args = getArguments();

		this.speed = Integer.parseInt(args[0].toString()); 
		this.agentName = getLocalName();
		info = new Info();
		try {
			startContainerName = getContainerController().getContainerName();
			startTourName = getTour(startContainerName);
		} catch (ControllerException e) {
			System.out.println("Erreur getContainerName() ");
			return;
		}
		Random r = new Random();
		this.height = 8 + r.nextInt(12 - 8);
		synchronized (monitor) {
			info.addAgent(this.agentName);
		}
		addBehaviour(new Idle(this, 1_000));
	}
	
	
	
	private String getTour(String containerName){
		
		return (containerName.equals("Main-Container") == true)? "tour1" : 	"tour2";
	}
	
	private void initialisationTours(String destination){
		destTourName = getTour(destination);
	}
	private boolean checkContainerName(String name){
		if(name.equals("Main-Container") || name.equals("Container-1")){
			return true;
		}
		return false;
	}
	
	
	private void responseToUser(int demande) {

		String msg;
		switch(demande){
		case 0:
			msg = agentName+" : La durée restante est :"+dureeRestante+" secondes\n";
			break;
		case 1:
			distanceParcourue = (speed * tempsEcoule)/3600.0;
			msg = agentName+" :L a distance parcourue est de "+distanceParcourue+" km\n";
			break;
		case 2:
			distanceRestante =  (speed *dureeRestante)/3600.0;
			msg = agentName+" : La distance restante est de "+distanceRestante+" km\n";
			break;
		default:
			msg = agentName+" : Valeur de demande inconnue"+helpDemandInfo()+"\n";
		}
		System.out.println(msg);
	}

	private Double getDistanceParcourue(){
		distanceParcourue = (speed * tempsEcoule)/3600.0;
		return distanceParcourue;
	}

	private	String helpDemandInfo(){
		return "Valeur à saisir pour effectuer une demande sur le vol:\n"
				+ "0 --> demande du temps restant\n"
				+ "1 --> demande de la distance parcourue\n"
				+ "2 --> demande de la distance restante\n";
	}
	
	class Idle extends TickerBehaviour {

		public Idle(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			
			ACLMessage msgReceived = receive();
			if (msgReceived != null) {
				if (msgReceived.getPerformative() == ACLMessage.INFORM) {
					//	System.out.println("Idle I received a message : " + msgReceived.getContent());
					//String[] msg = msgReceived.getContent().split(",");
					String msg = msgReceived.getContent();
						if(!checkContainerName(msg)){
							System.out.println("Container  "+msg+" inconnu");
						}
						if(startContainerName.equals(msg)){
							System.out.println("Mes containers de départ et d'arrivée doivent être differents !!!!");
							return;
						}
						dureeRestante = (long) ((Info.DISTANCE * 3600.0) / speed);
						vol = new Flight(msg,dureeRestante);
						initialisationTours(vol.destContainerName);
						addBehaviour(new Sender(String.valueOf(Code.RUN_TAXI.ordinal()),startTourName));
						addBehaviour(new Run_Taxi_TakeOff(Avion.this, 1_000));
						removeBehaviour(this);
					
				} else {
					System.out.println("Format ACLM incorrect: " + msgReceived.getContent());
				}
			}
		}
	}

	class Sender extends OneShotBehaviour{
		private String message;
		private String name;
		public Sender(String message,String name) {
			this.message = message;
			this.name = name;
		}
		@Override
		public void action() {
			ACLMessage msgSend = new ACLMessage(ACLMessage.REQUEST);
			msgSend.addReceiver(new AID(name,AID.ISLOCALNAME));
			msgSend.setContent(message); 
			send(msgSend);
		}
	}

	class WaitTimeTakeOff extends TickerBehaviour{

		public WaitTimeTakeOff(Agent a, long period) {
			super(a, period);

		}

		@Override
		protected void onTick() {
			System.out.println(agentName+" : vol en cours ...");
			addBehaviour(new Run_Fly(Avion.this,1_000));
			addBehaviour(new ResponseToUser(Avion.this, 1_000));
			removeBehaviour(this);

		}

	}

	class Run_Taxi_TakeOff extends TickerBehaviour {

		public Run_Taxi_TakeOff(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			ACLMessage msgReceived = receive();
			if (msgReceived != null) {
				if (msgReceived.getPerformative() == ACLMessage.AGREE) {
					dureeRestante = Integer.parseInt(msgReceived.getContent());
					addBehaviour(new WaitTimeTakeOff(Avion.this, dureeRestante * 1_000));
					System.out.println(agentName+" : Run_Taxi_TakeOff --> En cours");
					removeBehaviour(this);

				} else {
					if(msgReceived.getPerformative() == ACLMessage.REQUEST){
						System.out.println("L'avion n'a pas encore decollé!\nLa durée jusqu'à destination est de "+distanceTotale+" (m)");		
					}else{
						System.out.println(msgReceived.getContent());
					}
				}
			}

		}
	}
	
	class SenderOfNego extends TickerBehaviour{
		private List<String> agents;
		private HashMap<String, ACLMessage> messages;
		private ACLMessage msgSend;
		
		public SenderOfNego(Agent a, long period) {
			super(a, period);
			agents = info.getAgents();
			agents.forEach(agent ->{
				msgSend = new ACLMessage(ACLMessage.REQUEST);
				msgSend.addReceiver(new AID(agent,AID.ISLOCALNAME));
				String msg = String.valueOf(Code.NEGOTIATION.ordinal())+","+String.valueOf(getDistanceParcourue())+","+height+destTourName;
				msgSend.setContent(msg); 
				messages.put(agent, msgSend);
			});
		}

		@Override
		protected void onTick() {
			if(!negociating){
				messages.values().forEach(msg -> send(msg));
			}
		}
	}
	
	class SenderToResolveConflit extends OneShotBehaviour{
		private String message;
		private String name;
		
		
		public SenderToResolveConflit(String name, String message) {
			this.name = name;
			this.message = message;
		}

			@Override
			public void action() {
				ACLMessage msgSend = new ACLMessage(ACLMessage.PROPOSE);
				msgSend.addReceiver(new AID(name,AID.ISLOCALNAME));
				msgSend.setContent(message); 
				send(msgSend);
			}
	}
	
	
	class GestionNegociation extends TickerBehaviour{
		Random random = new Random();
		int newAltitude = 0;
		public GestionNegociation(Agent a, long period) {
			super(a, period);
			addBehaviour(new SenderOfNego(Avion.this, 1_000));
		}

		@Override
		protected void onTick() {
			
			// Receive Message
			ACLMessage msgReceived = receive();
			if (msgReceived != null) {
				if(msgReceived.getPerformative() == ACLMessage.REQUEST){
					try{
						String []args = msgReceived.getContent().split(",");
						int demande = Integer.parseInt(args[0]);
						if(demande == Code.NEGOTIATION.ordinal()){
							negociating = true;
							double distanceParcourue = Double.parseDouble(args[1]);
							int altitude = Integer.parseInt(args[2]);
							String destination = args[3];
							if(height == altitude){
								
								//Vérification perimetre de 1km
								if(destTourName.equals(destination)){
									
									sendNewAltitude(distanceParcourue, altitude);
								}else{
									distanceParcourue = info.DISTANCE - distanceParcourue;
									sendNewAltitude(distanceParcourue, altitude);
								}
							}
						}
					}catch (NumberFormatException e) {
						
						e.getStackTrace(); //Devrait jamais arriver
					}
				}else if( msgReceived.getPerformative() == ACLMessage.PROPOSE){
					
					//L'avion change d'altitude
					height = Integer.parseInt(msgReceived.getContent());
				}else{
					System.out.println(msgReceived.getContent());
				}
			}
			synchronized (monitor) {
				if(dureeRestante == 0) {
					removeBehaviour(this);
				}
			}
		}

		private void sendNewAltitude(double distanceParcourue, int altitude) {
			//Calcul de la distance separant les deux avions
			double ecart = getDistanceParcourue() - distanceParcourue;
			ecart = (ecart < 0) ? -ecart : ecart;
			
			if(ecart < 1){
				System.out.println("Il y a eu un conflit");
				do{
					newAltitude  = 8 + random.nextInt(12 - 8);
				}while(newAltitude == altitude);
				addBehaviour(new SenderToResolveConflit(agentName,String.valueOf(newAltitude)));
				negociating = false;
			}
		}
	}
	
	

	class ResponseToUser extends TickerBehaviour{

		public ResponseToUser(Agent a, long period) {
			super(a, period);
			// TODO Auto-generated constructor stub
		}

		@Override
		protected void onTick() {

			// Receive Message
			ACLMessage msgReceived = receive();
			if (msgReceived != null) {
				if(msgReceived.getPerformative() == ACLMessage.REQUEST){
					try{
						int demande = Integer.parseInt(msgReceived.getContent());
						synchronized (monitor) {
							responseToUser(demande);
						}
					}catch (NumberFormatException e) {
						responseToUser(-1);
					}
				}else{
					System.out.println(msgReceived.getContent());
				}
			}
			synchronized (monitor) {
				if(dureeRestante == 0) {
					removeBehaviour(this);
				}
			}
		}
	}

	class ReceiveFly extends TickerBehaviour{

		public ReceiveFly(Agent a, long period) {
			super(a, period);

		}

		@Override
		protected void onTick() {

			ACLMessage msgReceived = receive();
			if (msgReceived != null) {
				if (msgReceived.getPerformative() == ACLMessage.AGREE) {
					System.out.println(agentName+" : Atterissage en cours...");
					dureeRestante = Integer.parseInt(msgReceived.getContent());
					addBehaviour(new Run_Landing(Avion.this,dureeRestante * 1_000));
					removeBehaviour(this);
				} else {
					System.out.println(msgReceived.getContent());
				}
			}

		}

	}

	class ReceiveLanding extends TickerBehaviour{

		public ReceiveLanding(Agent a, long period) {
			super(a, period);

		}

		@Override
		protected void onTick() {
			ACLMessage msgReceived = receive();
			if (msgReceived != null) {

				if (msgReceived.getPerformative() == ACLMessage.AGREE) {
					dureeRestante = Integer.parseInt(msgReceived.getContent());
					System.out.println(agentName+" : Run_Taxi_TakeOff --> En cours");
					addBehaviour(new WaitTimeLanding(Avion.this, dureeRestante * 1_000));
					removeBehaviour(this);
				}else if(msgReceived.getPerformative() == ACLMessage.REQUEST) {

					int demande = Integer.parseInt(msgReceived.getContent());
					responseToUser(demande);
				}
				else{
					System.out.println(agentName+" : "+msgReceived.getContent());
				}
			}
		}

	}


	class Run_Fly extends TickerBehaviour {

		public Run_Fly(Agent a,long period) {
			super(a, period);
			dureeRestante = vol.duration;
		}

		//TODO lorsque l'utilisateur veut savoir la distance restante etc

		@Override
		protected void onTick() {

			if(dureeRestante > 0.0) {
				synchronized (monitor) {
					dureeRestante--;
					tempsEcoule++;
				}

			} else {
				System.out.println("Période de vol écoulée");
				// Send message
				System.out.println(agentName+" : Demande d'autorisation d'atterissage");
				addBehaviour(new Sender(String.valueOf(Code.LANDING.ordinal()), destTourName));
				removeBehaviour(this);
				addBehaviour(new ReceiveFly(Avion.this, 1_000));
			}
		}

	}

	class Run_Landing extends TickerBehaviour {

		public Run_Landing(Agent a,long period) {
			super(a , period);
		}

		@Override
		protected void onTick() {
			addBehaviour(new Run_Taxi_Landing(Avion.this, 1_000));
			removeBehaviour(this);


		}
	}

	class Run_Taxi_Landing extends TickerBehaviour {

		public Run_Taxi_Landing(Agent a, long period) {
			super(a,period);
		}

		@Override
		protected void onTick() {

			addBehaviour(new Sender(String.valueOf(Code.RUN_TAXI.ordinal()), destTourName));
			removeBehaviour(this);
			addBehaviour(new ReceiveLanding(Avion.this,1_000));

		}
	}
	class WaitTimeLanding extends TickerBehaviour{

		public WaitTimeLanding(Agent a, long period) {
			super(a, period);

		}

		@Override
		protected void onTick() {

			System.out.println("Etat Idle atteint");
			startTourName = destTourName;
			Location destination = new ContainerID(vol.destContainerName,null);
			doMove(destination);
			addBehaviour(new Idle(Avion.this, 1000));
			removeBehaviour(this);

		}

	}
}
