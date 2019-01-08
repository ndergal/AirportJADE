package Aeroport;

import jade.core.Agent;

import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("serial")
public class TourDeControle extends Agent {

	// Les durées sont en seconde
	public final static int TAKEOFF_TIME = 10;
	public final static int LANDING_TIME = 15;
	public final static int RUN_TAXI_TIME = 5;
	public final static int UNIT = 1000;

	public static enum Code {
		RUN_TAXI, LANDING, TAKEOFF,NEGOTIATION
	};
	
	static class Info implements java.io.Serializable {
		
		public static final int DISTANCE = 200; //200km entre les deux containers
		private final ArrayList<String> agents = new ArrayList<>();
		
		public void addAgent (String agent){
			agents.add(agent);
		}
		public List<String> getAgents (){
			return Collections.unmodifiableList(agents);
		}
		
	}

	private String name;
	//private final int capacity = 1; // Par défault
	private final LinkedList<ACLMessage> listeAvionsPrets = new LinkedList<>();
	private boolean pisteOccupée = false;
	private int dureeNecessaire = 0;

	@Override
	protected void setup() {

		Object[] args = getArguments();
		if (args.length != 0) {
			name = args[0].toString();
		}else{
			name = "alpha";
		}

		System.out.println("Salut!\nJe suis la tour de contrôle " + name + " et ma"
				+ " capacité de decollage/atterissage est de 1 avion simultanement");

		addBehaviour(new ReceptionAvion(this, 1_000));
		addBehaviour(new Run(this, 1_000));
	}

	class ReceptionAvion extends TickerBehaviour {

		public ReceptionAvion(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			ACLMessage request = receive();
			if (request != null) {
				ACLMessage response = request.createReply();

				if (request.getPerformative() == ACLMessage.REQUEST) {
					int code = Integer.parseInt(request.getContent());
					if (code != 0 && code != 1 && code != 2) {
						System.out.println("error, unknown this code: " + code);
						return;
					} else if (code == Code.RUN_TAXI.ordinal()) {
						System.out.println("Bienvenue à la tour de contrôle " + name + " !");
						response.setPerformative(ACLMessage.AGREE);
						response.setContent(String.valueOf(RUN_TAXI_TIME));
						send(response);
					} else {
						synchronized (listeAvionsPrets) {
							if (pisteOccupée) {
								response.setPerformative(ACLMessage.REFUSE);
								response.setContent("Désolé la piste est actuellement occupée, merci de patienter");
								send(response);
							}

							listeAvionsPrets.add(request);
						}
					}					
				} else {
					System.out.println("ERROR: " + request.getContent());
				}
			}

		}

	}

	class Run extends TickerBehaviour {

		public Run(Agent a, long period) {
			super(a, period);
		}

		@Override
		protected void onTick() {
			ACLMessage request;
			int code;

			if (dureeNecessaire > 0) {
				dureeNecessaire--;
			} else {
				// System.out.println("La piste est libre");
				synchronized (listeAvionsPrets) {
					pisteOccupée = false;
				}
			}
			synchronized (listeAvionsPrets) {
				if (!pisteOccupée && !listeAvionsPrets.isEmpty()) {

					request = listeAvionsPrets.removeFirst();
					pisteOccupée = true;
					code = Integer.parseInt(request.getContent());
					ACLMessage response = request.createReply();
					response.setPerformative(ACLMessage.AGREE);
					if (code == Code.TAKEOFF.ordinal()) {
						dureeNecessaire = TAKEOFF_TIME;
					} else if (code == Code.LANDING.ordinal()) {
						dureeNecessaire = LANDING_TIME;
					}
					response.setContent(String.valueOf(dureeNecessaire));
					send(response);
				}
			}
		}

	}

}
