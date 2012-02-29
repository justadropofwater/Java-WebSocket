package net.tootallnate.websocket;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;

class ChannelWrapper {
	private SSLEngine sslEngine;
	private SocketChannel channel;
	
	/**raw incoming data*/
	private ByteBuffer insrc;
	/**decoded incoming data*/
	private ByteBuffer indest;
	
	/**raw/not encoded outgoing data*/
	private ByteBuffer outsrc;
	/**encoded outgoing data*/
	private ByteBuffer outdest;
	
	private int lastreadcount;
	
	/** Whether this peer acts as client*/
	private boolean clientmode;
	
	private ByteBuffer emptybb = ByteBuffer.allocate( 0 );
	
	public ChannelWrapper( SocketChannel channel, boolean clientmode ) throws SSLException {
		if(channel==null)
			throw new IllegalArgumentException( "SocketChannel may not be null" );
		this.channel = channel;
		this.clientmode = clientmode;
		ensureInit();
	}
	
	/**If there is a handshake to be processed this method does it and returns false*/
	private boolean processWrap(ByteBuffer fromuser ) throws SSLException,IOException{
		HandshakeStatus status = sslEngine.getHandshakeStatus();
		if( status == HandshakeStatus.NOT_HANDSHAKING){
			System.out.println("handshake finished");
		}
		SSLEngineResult res = wrap( fromuser  );
		writeSocketAll( outdest );
		outdest.clear();
		return false;
	}
	
	private void processUnWrap( ByteBuffer foruser) throws SSLException, IOException, ClosedException{
		HandshakeStatus status = sslEngine.getHandshakeStatus();
		if( status == HandshakeStatus.NOT_HANDSHAKING){
			System.out.println("handshake finished");
		}
		SSLEngineResult res = unwrap( foruser );
		switch ( res.getHandshakeStatus() ) {
			case NEED_TASK:
				provideTask();
				break;
			default :
				break;
		}
		processWrap(emptybb);
	}
	
	private void provideTask(){
		System.out.println("provideTask:");
		//exec.execute(  );
		sslEngine.getDelegatedTask().run();
	}
	
	/**Inits the engine if not already done
	 * Returns true if the engine is useable.
	 * @throws SSLException */
	private boolean ensureInit() throws SSLException{
		if(sslEngine!=null)
			return true;
		else{
			InetSocketAddress address = (InetSocketAddress) channel.socket().getRemoteSocketAddress();
			if(address==null){//address will be null if the channel is not yet connected
				return false;
			}
			try {
				//TODO customize context creation
				sslEngine = createSSLContext( clientmode, "keystore.jks", "hallo12345" ).createSSLEngine( /*address.getHostName(), address.getPort()*/);
			} catch ( Exception e1 ) {
				throw new RuntimeException( e1 );
			} 
			sslEngine.setEnabledCipherSuites( sslEngine.getSupportedCipherSuites() );
			sslEngine.setEnabledProtocols( sslEngine.getSupportedProtocols() );
			sslEngine.setUseClientMode( clientmode );
			
			SSLSession ses = sslEngine.getSession();
			insrc = ByteBuffer.allocate( ses.getApplicationBufferSize() );
			indest = ByteBuffer.allocate( ses.getPacketBufferSize() );
			outsrc= ByteBuffer.allocate( ses.getApplicationBufferSize() );
			outdest = ByteBuffer.allocate( ses.getPacketBufferSize() );
			
			sslEngine.beginHandshake();
			return true;
			
		}
	}
	
	private SSLEngineResult wrap( ByteBuffer src ) throws SSLException{
		System.out.println("wrap:");
		outdest.rewind();
		src.mark();
		SSLEngineResult res = sslEngine.wrap( src, this.outdest );
		Status s = res.getStatus();
		outdest.flip();
		if( s == Status.OK)
			return res;
		else if( s == Status.BUFFER_OVERFLOW ){
			System.out.println("Status.BUFFER_OVERFLOW:");
			outdest = ByteBuffer.allocate( (int)(outdest.capacity()*1.2) );
			src.reset();
			return wrap( src );
		}
		else if( s == Status.BUFFER_UNDERFLOW ){
			System.out.println("Status.BUFFER_UNDERFLOW");
		}
		return res;
	}
	
	private SSLEngineResult unwrap( ByteBuffer userdatabuf ) throws SSLException,IOException, ClosedException{
		readSocket( insrc );
		insrc.flip();
		SSLEngineResult res = sslEngine.unwrap( insrc, userdatabuf );
		Status s = res.getStatus();
		if( s == Status.OK)
			return res;
		else if( s == Status.BUFFER_OVERFLOW ){
			System.out.println("BUFFER_OVERFLOW");
		}
		else if( s == Status.BUFFER_UNDERFLOW ){
			if(!insrc.hasRemaining()){
				System.out.println("wrap BUFFER_UNDERFLOW");
			}
			else{
				insrc.position( insrc.limit() );
				insrc.limit( insrc.capacity() );
			}
		}
		return res;
	}
	
	
	public int read(ByteBuffer userdata) throws IOException{
		int pos1=userdata.position();
		try {
			System.out.println("read: ");
			if(!ensureInit()){
				return 0;
			}
			processUnWrap( userdata);
			System.out.println("read state:"+sslEngine.getHandshakeStatus()+lastreadcount);
			return userdata.position()-pos1;
		} catch ( ClosedException e ) {
			return -1;
		}
	}
	
	public int write(ByteBuffer buffer) throws IOException{
		System.out.println("write: ");
		if(!ensureInit()){
			return 0;
		}
		while(sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP){
			processWrap(buffer );
		}
		processWrap(buffer );
		int rem = buffer.remaining();
		System.out.println("write state:"+sslEngine.getHandshakeStatus()+lastreadcount);
		return rem-buffer.remaining();
	}
	
	private void writeSocketAll(ByteBuffer b) throws IOException{
		//System.out.println("SOcketwrite:"+new String( b.array(), b.position(), b.remaining() ));
		while(b.hasRemaining()){
			channel.write( b );
		}
	}
	
	private void readSocket(ByteBuffer b) throws IOException, ClosedException{
		lastreadcount = channel.read( b );
		//System.out.println("SOcketwrite:"+new String( b.array(), 0, b.position() ));
		if(lastreadcount == -1){
			channel.close();
			throw new ClosedException();
		}
	}
	public void close() throws IOException{
		sslEngine.closeOutbound();
	}
	public SocketAddress getRemoteSocketAddress() {
		return channel.socket().getRemoteSocketAddress();
	}
	public SocketAddress getLocalSocketAddress() {
		return channel.socket().getLocalSocketAddress();
	}
	
	private class ClosedException extends Exception{
		
	}
	
	public static SSLContext createSSLContext(
			boolean clientMode, 
			String keystore, 
			String password) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, KeyManagementException, UnrecoverableKeyException  {
		// Create/initialize the SSLContext with key material
		char[] passphrase = password.toCharArray();
		// First initialize the key and trust material.
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream(keystore), passphrase);
		SSLContext sslContext = SSLContext.getInstance("TLS");
		
		if (clientMode) {
			// TrustManager's decide whether to allow connections.
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ks);
			sslContext.init(null, tmf.getTrustManagers(), null);
			
		} else {
			// KeyManager's decide which key material to use.
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, passphrase);
			sslContext.init(kmf.getKeyManagers(), null, null);
		}
		return sslContext;
	}
	class TrustAll extends TrustManagerFactory{
		protected TrustAll( TrustManagerFactorySpi factorySpi , Provider provider , String algorithm ) {
			super( factorySpi, provider, algorithm );
			// TODO Auto-generated constructor stub
		}
	}
	
}