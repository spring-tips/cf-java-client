@Grab("spring-boot-starter-actuator")
@RestController
class GreetingsRestController {

  @Value("\${vcap.application.application_id:APP-ID-NOT-FOUND}")
  String appId

  @Value("\${vcap.application.instance_index:INSTANCE-ID-NOT-FOUND}")
  String index

  @GetMapping("/hi")
  def hi(){
   println( "app : " + appId )
   println( "index: " + index )
   "hello, world"
  }
 }
